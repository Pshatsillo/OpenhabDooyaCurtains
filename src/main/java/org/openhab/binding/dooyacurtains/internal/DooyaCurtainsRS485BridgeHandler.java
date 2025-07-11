/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.dooyacurtains.internal;

import static org.openhab.binding.dooyacurtains.internal.DooyaCurtainsBindingConstants.BINDING_ID;
import static org.openhab.binding.dooyacurtains.internal.DooyaCurtainsBindingConstants.PROGRAM_SWITCH;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortEvent;
import org.openhab.core.io.transport.serial.SerialPortEventListener;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DooyaCurtainsRS485BridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class DooyaCurtainsRS485BridgeHandler extends BaseBridgeHandler implements SerialPortEventListener {

    private final Logger logger = LoggerFactory.getLogger(DooyaCurtainsRS485BridgeHandler.class);
    private final SerialPortManager serialPortManager;
    private @Nullable SerialPort serialPort;
    private @Nullable InputStream inputStream;
    private @Nullable OutputStream outputStream;
    private @Nullable ScheduledFuture<?> pollingTask;
    public List<DooyaCurtainsPooler> requestsList = new ArrayList<>();

    public DooyaCurtainsRS485BridgeHandler(Bridge thing, SerialPortManager serialPortManager) {
        super(thing);
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void initialize() {
        DooyaCurtainsConfiguration config = getConfigAs(DooyaCurtainsConfiguration.class);
        if (config.serialPort.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Port must be set!");
            return;
        } else {
            SerialPortIdentifier portId = serialPortManager.getIdentifier(config.serialPort);
            if (portId == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                        "Port " + config.serialPort + " is unknown!");
                serialPort = null;
            } else {
                updateStatus(ThingStatus.UNKNOWN);

                List<Channel> channelList = new ArrayList<>();
                List<Channel> existingChannelList = new LinkedList<>(thing.getChannels());
                Configuration channelConfiguration = new Configuration();
                channelConfiguration.put("Address", "FEFE");
                ChannelUID programAddressUID = new ChannelUID(thing.getUID(), PROGRAM_SWITCH);
                Channel programAddress = ChannelBuilder.create(programAddressUID)
                        .withType(new ChannelTypeUID(BINDING_ID, PROGRAM_SWITCH))
                        .withConfiguration(channelConfiguration).build();

                if (existingChannelList.stream().anyMatch(cn -> cn.getUID().equals(programAddress.getUID()))) {
                    Channel foundedChannel = existingChannelList.stream()
                            .filter(cn -> cn.getUID().equals(programAddress.getUID())).findFirst().get();
                    channelList.add(foundedChannel);
                    existingChannelList.remove(foundedChannel);
                } else {
                    channelList.add(programAddress);
                }

                ThingBuilder thingBuilder = editThing();
                thingBuilder.withChannels(channelList);
                updateThing(thingBuilder.build());
                updateStatus(ThingStatus.ONLINE);
                updateState(programAddress.getUID().getId(), OnOffType.OFF);

            }

        }
        scheduler.execute(this::connect);
        pollingTask = scheduler.scheduleWithFixedDelay(this::poll, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        Iterator<DooyaCurtainsPooler> iterator = requestsList.iterator();
        try {
            while (iterator.hasNext()) {
                DooyaCurtainsPooler dooyaCurtainsPooler = iterator.next();
                DooyaCurtainsHandler handler = dooyaCurtainsPooler.dooyaCurtainsHandler;
                if ((handler != null) && (dooyaCurtainsPooler.channel != null)) {
                    byte[] answer = send(dooyaCurtainsPooler.request, dooyaCurtainsPooler.request.length + 2);
                    if (answer[0] == 0x55) {
                        handler.response(answer, dooyaCurtainsPooler.channel);
                    }
                }
                iterator.remove();
                // Thread.sleep(100);
            }
        } catch (Exception e) {
            logger.error("Polling error {}", e.getMessage());
        }
    }

    private synchronized void connect() {
        DooyaCurtainsConfiguration config = getConfigAs(DooyaCurtainsConfiguration.class);
        SerialPortIdentifier portId = serialPortManager.getIdentifier(config.serialPort);
        if (portId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Port " + config.serialPort + " is unknown!");
            serialPort = null;
            disconnect();
        } else if (!isConnected()) {
            try {
                SerialPort serial = portId.open(getThing().getUID().toString(), 2000);
                serial.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                try {
                    InputStream inputStream = this.inputStream;
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    inputStream = null;
                }
                try {
                    OutputStream outputStream = this.outputStream;
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException e) {
                    outputStream = null;
                }

                inputStream = serial.getInputStream();
                outputStream = serial.getOutputStream();
                serialPort = serial;

                updateStatus(ThingStatus.ONLINE);

            } catch (final IOException ex) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, "I/O error!");
                logger.error("{}", ex.getMessage());
            } catch (PortInUseException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, "Port is in use!");
                logger.error("{}", e.getMessage());
            } catch (UnsupportedCommOperationException e) {
                logger.error("{}", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    private boolean isConnected() {
        return serialPort != null && inputStream != null && outputStream != null;
    }

    public byte[] send(byte[] data, int answerLenght) {
        DooyaCurtainsCRC16Modbus crc = new DooyaCurtainsCRC16Modbus();
        for (int d : data) {
            crc.update(d);
        }
        byte[] byteStr = new byte[2];
        byteStr[0] = (byte) ((crc.getValue() & 0x000000ff));
        byteStr[1] = (byte) ((crc.getValue() & 0x0000ff00) >>> 8);
        byte[] reqestString = new byte[data.length + 2];
        System.arraycopy(data, 0, reqestString, 0, data.length);
        reqestString[reqestString.length - 2] = byteStr[0];
        reqestString[reqestString.length - 1] = byteStr[1];
        StringBuilder sb = new StringBuilder(reqestString.length * 2);
        for (byte b : reqestString)
            sb.append(String.format("%02X ", b));
        logger.debug("   send: {}", sb);

        try {
            OutputStream out = outputStream;
            if (out != null) {
                out.write(reqestString);
                out.flush();
                Thread.sleep(200);
            }
        } catch (IOException | InterruptedException ignored) {

        }

        byte[] frame = new byte[answerLenght];
        InputStream in = inputStream;
        if (in != null) {
            try {
                while (in.available() > 0) {
                    var result = in.read(frame);
                    logger.trace("response {}", result);
                }
                StringBuilder sbl = new StringBuilder(frame.length * 2);
                for (byte b : frame)
                    sbl.append(String.format("%02X ", b));
                logger.debug("receive: {}", sbl);
            } catch (IOException e1) {
                logger.debug("Error reading from serial port: {}", e1.getMessage(), e1);
            }
        }
        return frame;
    }

    private void disconnect() {
        logger.debug("disconnecting port...");
        if (thing.getStatus() != ThingStatus.REMOVING) {
            updateStatus(ThingStatus.OFFLINE);
        }
        synchronized (this) {
            try {
                InputStream inputStream = this.inputStream;
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                inputStream = null;
            }
            try {
                OutputStream outputStream = this.outputStream;
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                outputStream = null;
            }
            try {
                SerialPort serialPort = this.serialPort;
                if (serialPort != null) {
                    serialPort.close();
                    this.serialPort = null;
                    logger.debug("disconnected port");
                }
            } catch (Exception exception) {
                logger.error("disconnected port {}", exception.getMessage());
            }
        }
    }

    @Override
    public void dispose() {
        logger.debug("Disposing...");
        ScheduledFuture<?> pollingTask = this.pollingTask;
        if (pollingTask != null) {
            pollingTask.cancel(true);
        }
        disconnect();
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command.equals(OnOffType.ON)) {
            Channel program = thing.getChannel(channelUID);
            byte[] address = new byte[2];
            if (program != null) {
                if (program.getConfiguration().get("Address") != null) {
                    address = HexFormat.of().parseHex(program.getConfiguration().get("Address").toString());
                }
            }
            byte[] data = new byte[] { 0x55, 0x00, 0x00, 0x02, 0x00, 0x02, address[0], address[1] };
            byte[] answer = send(data, 15);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {

            }
            if (answer[8] == address[0] && answer[9] == address[1]) {
                updateState(channelUID, OnOffType.OFF);
            }

        }
    }

    @Override
    public void serialEvent(SerialPortEvent serialPortEvent) {
        logger.warn("Serial event {}", serialPortEvent.getEventType());
    }
}

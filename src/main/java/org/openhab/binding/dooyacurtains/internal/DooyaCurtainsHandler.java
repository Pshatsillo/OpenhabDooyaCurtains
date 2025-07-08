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

import static org.openhab.binding.dooyacurtains.internal.DooyaCurtainsBindingConstants.INVERTED;
import static org.openhab.binding.dooyacurtains.internal.DooyaCurtainsBindingConstants.POSITION;
import static org.openhab.binding.dooyacurtains.internal.DooyaCurtainsBindingConstants.STATE;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DooyaCurtainsHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class DooyaCurtainsHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(DooyaCurtainsHandler.class);
    @Nullable
    DooyaCurtainsRS485BridgeHandler bridgeHandler;
    private @Nullable DooyaCurtainsConfiguration config;
    private @Nullable ScheduledFuture<?> pollingTask;
    byte[] address = new byte[2];

    public DooyaCurtainsHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // logger.debug("Command {}", command);
        if (command instanceof RefreshType) {

        } else {
            DooyaCurtainsRS485BridgeHandler bridgeHandler = this.bridgeHandler;
            if (bridgeHandler != null) {
                if (POSITION.equals(channelUID.getId())) {
                    logger.debug("Command Position");
                    int counter = 0;
                    byte[] data = new byte[] { 0x55, address[0], address[1], 0x03, 0x04,
                            Byte.parseByte(command.toString()) };
                    byte[] status = new byte[6];
                    while (!Arrays.equals(status, data)) {
                        if (counter > 5) {
                            logger.debug("Can not send command Position");
                            break;
                        }
                        System.arraycopy(bridgeHandler.send(data, 8), 0, status, 0, 6);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                        counter++;
                    }
                } else if (STATE.equals(channelUID.getId())) {
                    if (command.toString().equals("OPEN")) {
                        logger.debug("Command OPEN");
                        int counter = 0;
                        byte[] data = new byte[] { 0x55, address[0], address[1], 0x03, 0x01 };
                        byte[] status = new byte[5];
                        while (!Arrays.equals(status, data)) {
                            if (counter > 5) {
                                logger.debug("Can not send command OPEN");
                                break;
                            }
                            System.arraycopy(bridgeHandler.send(data, 7), 0, status, 0, 5);
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                            counter++;
                        }
                    }
                    if (command.toString().equals("CLOSE")) {
                        logger.debug("Command CLOSE");
                        byte[] data = new byte[] { 0x55, address[0], address[1], 0x03, 0x02 };
                        byte[] status = new byte[5];
                        int counter = 0;
                        while (!Arrays.equals(status, data)) {
                            if (counter > 5) {
                                logger.debug("Can not send command CLOSE");
                                break;
                            }
                            System.arraycopy(bridgeHandler.send(data, 7), 0, status, 0, 5);
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                            counter++;
                        }
                    }
                    if (command.toString().equals("STOP")) {
                        logger.debug("Command STOP");
                        byte[] data = new byte[] { 0x55, address[0], address[1], 0x03, 0x03 };
                        byte[] status = new byte[5];
                        int counter = 0;
                        while (!Arrays.equals(status, data)) {
                            if (counter > 5) {
                                logger.debug("Can not send command STOP");
                                break;
                            }
                            System.arraycopy(bridgeHandler.send(data, 7), 0, status, 0, 5);
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                            counter++;
                        }
                    }
                } else if (INVERTED.equals(channelUID.getId())) {
                    logger.debug("Command {}", command);
                    int direction = 0;
                    if (command.toString().equals("REVERSE")) {
                        direction = 1;
                    }
                    byte[] data = new byte[] { 0x55, address[0], address[1], 0x02, 0x03, 0x01, (byte) direction };
                    byte[] status = new byte[7];
                    int counter = 0;
                    while (!Arrays.equals(status, data)) {
                        if (counter > 5) {
                            logger.debug("Can not send command INVERTED");
                            break;
                        }
                        System.arraycopy(bridgeHandler.send(data, 9), 0, status, 0, 7);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                        counter++;
                    }
                }
            }
        }
    }

    @Override
    public void initialize() {
        config = getConfigAs(DooyaCurtainsConfiguration.class);
        bridgeHandler = getBridgeHandler();
        DooyaCurtainsRS485BridgeHandler bridgeHandler = this.bridgeHandler;
        if (bridgeHandler != null) {
            DooyaCurtainsConfiguration config = this.config;
            if (config != null) {
                address = HexFormat.of().parseHex(config.address);
                byte[] data = new byte[] { 0x55, address[0], address[1], 0x01, 0x02, 0x01 };
                int reconnect = 0;
                while (!bridgeHandler.getThing().getStatus().equals(ThingStatus.ONLINE)) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                    if (reconnect == 10) {
                        logger.error("Bridge is offline during 10 seconds");
                        updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                                "Bridge is offline during 10 seconds");
                        break;
                    }
                    reconnect++;
                }
                var status = bridgeHandler.send(data, 8);
                if (status[0] == 0x55) {
                    updateStatus(ThingStatus.ONLINE);
                    pollingTask = scheduler.scheduleWithFixedDelay(this::poll, 0, 100, TimeUnit.MILLISECONDS);
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                }
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    private void poll() {
        DooyaCurtainsRS485BridgeHandler bridgeHandler = this.bridgeHandler;
        if (bridgeHandler != null) {
            for (Channel channel : getThing().getChannels()) {
                if (isLinked(channel.getUID().getId())) {
                    if (channel.getUID().getId().equals(POSITION)) {
                        byte[] data = new byte[] { 0x55, address[0], address[1], 0x01, 0x02, 0x01 };
                        byte[] answer = bridgeHandler.send(data, 8);
                        if (!String.format("%02X", answer[5]).equals("FF")) {
                            logger.debug("Position is: {}", answer[5]);
                            try {
                                updateState(channel.getUID(), PercentType.valueOf(String.valueOf(answer[5])));
                            } catch (Exception ignored) {
                                logger.debug("Errror position is: {}", answer[5]);
                            }

                        } else {
                            StringBuilder sbl = new StringBuilder(answer.length * 2);
                            for (byte b : answer)
                                sbl.append(String.format("%02X ", b));
                            logger.debug("Device does not set limits. Answer is: {}", sbl);
                        }
                    }
                    if (channel.getUID().getId().equals(STATE)) {
                        byte[] data = new byte[] { 0x55, address[0], address[1], 0x01, 0x05, 0x01 };
                        byte[] answer = bridgeHandler.send(data, 8);
                        if (!String.format("%02X", answer[5]).equals("FF") && answer[0] == 0x55) {
                            if (answer[5] == 0) {
                                logger.debug("Device state is: STOP");
                                updateState(channel.getUID(), StringType.valueOf("STOP"));
                            }
                            if (answer[5] == 1) {
                                logger.debug("Device state is: OPEN");
                                updateState(channel.getUID(), StringType.valueOf("OPEN"));
                            }
                            if (answer[5] == 2) {
                                logger.debug("Device state is: CLOSE");
                                updateState(channel.getUID(), StringType.valueOf("CLOSE"));
                            }
                        } else {
                            StringBuilder sbl = new StringBuilder(answer.length * 2);
                            for (byte b : answer)
                                sbl.append(String.format("%02X ", b));
                            logger.debug("Device state is: {}", sbl);
                        }
                    }
                    if (channel.getUID().getId().equals(INVERTED)) {
                        byte[] data = new byte[] { 0x55, address[0], address[1], 0x01, 0x03, 0x01 };
                        byte[] answer = bridgeHandler.send(data, 8);
                        if (answer[5] == 0 && answer[0] == 0x55) {
                            logger.debug("Device state is: DIRECT");
                            updateState(channel.getUID(), StringType.valueOf("DIRECT"));
                        }
                        if (answer[5] == 1) {
                            logger.debug("Device state is: REVERSE");
                            updateState(channel.getUID(), StringType.valueOf("REVERSE"));
                        }
                    }
                }
            }
        }
    }

    private @Nullable DooyaCurtainsRS485BridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            if (bridge.getHandler() instanceof DooyaCurtainsRS485BridgeHandler) {
                return (DooyaCurtainsRS485BridgeHandler) bridge.getHandler();
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> refreshPollingJob = this.pollingTask;
        if (refreshPollingJob != null && !refreshPollingJob.isCancelled()) {
            refreshPollingJob.cancel(true);
        }
        // this.pollingTask = refreshPollingJob;
        this.pollingTask = null;
        super.dispose();
    }
}

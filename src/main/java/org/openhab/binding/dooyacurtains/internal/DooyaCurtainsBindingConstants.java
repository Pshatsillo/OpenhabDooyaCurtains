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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link DooyaCurtainsBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class DooyaCurtainsBindingConstants {

    public static final String BINDING_ID = "dooyacurtains";

    // List of all Thing Type UIDs
    public static final ThingTypeUID CURTAIN_THING = new ThingTypeUID(BINDING_ID, "curtain");
    public static final ThingTypeUID RS485_BRIDGE = new ThingTypeUID(BINDING_ID, "rs485");

    // List of all Channel ids
    public static final String POSITION = "position";
    public static final String STATE = "state";
    public static final String INVERTED = "inverted";

    ///
    public static final String PROGRAM_SWITCH = "setaddress";
    // public static final String PROGRAM_ADDRESS = "address";
    // public static final String PROGRAM_STATUS = "status";
}

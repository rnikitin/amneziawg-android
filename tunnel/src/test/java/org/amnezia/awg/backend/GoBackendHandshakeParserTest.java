/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.backend;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GoBackendHandshakeParserTest {
    @Test
    public void parsesLastHandshakeWithoutSplittingWholeConfig() {
        final String config = "private_key=abc\npublic_key=def\nlast_handshake_time_sec=12345\nrx_bytes=10\n";
        assertEquals(12345L, GoBackend.parseLastHandshakeSeconds(config));
    }

    @Test
    public void returnsMinusOneWhenHandshakeIsMissing() {
        final String config = "private_key=abc\npublic_key=def\nrx_bytes=10\n";
        assertEquals(-1L, GoBackend.parseLastHandshakeSeconds(config));
    }

    @Test
    public void returnsMinusTwoWhenHandshakeIsMalformed() {
        final String config = "public_key=def\nlast_handshake_time_sec=abc\n";
        assertEquals(-2L, GoBackend.parseLastHandshakeSeconds(config));
    }

    @Test
    public void ignoresEmbeddedHandshakePrefixAndParsesLineStartMatch() {
        final String config = "note=last_handshake_time_sec=abc\nlast_handshake_time_sec=2468\n";
        assertEquals(2468L, GoBackend.parseLastHandshakeSeconds(config));
    }

    @Test
    public void parsesLastHandshakeWithoutTrailingNewline() {
        final String config = "private_key=abc\nlast_handshake_time_sec=12345";
        assertEquals(12345L, GoBackend.parseLastHandshakeSeconds(config));
    }

    @Test
    public void returnsMinusTwoWhenHandshakeIsEmpty() {
        final String config = "public_key=def\nlast_handshake_time_sec=\n";
        assertEquals(-2L, GoBackend.parseLastHandshakeSeconds(config));
    }
}

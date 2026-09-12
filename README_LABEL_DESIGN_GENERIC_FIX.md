# Generic Essae LFT Label Design Upload

This version accepts any valid `.LFT` label design selected from the phone. The bundled designs are examples only.

The direct transport follows the successful PC captures supplied for the two different label designs:
1. Initial session handshake
2. Label file-open frame
3. Second setup handshake
4. Label-design data frame with captured checksum scheme
5. Wait for `66 03 00 00 97 FF`
6. Client sends `11 01 00 00 EE FF`
7. Wait for `66 01 A0 05 F4 FE`
8. Complete

The final handshake is sent by the client after the upload ACK; it is not expected as an unsolicited scale packet.

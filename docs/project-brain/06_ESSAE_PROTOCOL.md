# Essae Protocol Notes

Known working protocol details preserved from reverse engineering:
- TCP/IP transport
- Port: 4321
- PLU records: 143 bytes
- Frames contain 10 records
- Price is encoded as a float at the known price offset (+71)
- Checksums are required
- Scale ACK behavior is part of successful upload handling
- LFT upload is supported by the working implementation

The existing Upload All -> Essae flow is considered the reference implementation.

Do not invent a new protocol or rewrite EssaeTransport without a verified reason and regression testing.

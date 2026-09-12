# Essae Scale Manager — Direct Batch Test 6

This build is based on the supplied ETLDRV 349-PLU Wireshark capture.

## Protocol findings implemented
- Setup sequence before PLU upload is reproduced from the capture.
- PLUs are batched 10 records per `22 xx` data frame (last frame may contain fewer).
- Each PLU record is 143 bytes. The final two bytes of each non-final record carry the next PLU number.
- First PLU number is carried in the 2-byte field in the frame header.
- Unit price is a 4-byte IEEE-754 little-endian float at record offset 71.
- Header byte 4 is the 8-bit two's-complement checksum of header bytes 0..3; byte 5 is FF.
- Frame checksum is the little-endian 16-bit complement `0x101FF - sum(all bytes before checksum)`.
- The scale acknowledgement is checked after every data frame.

## Test recommendation
Use the 133-PLU CSV first. Select all WEIGH PLUs and upload. Do not use PCS items until a real PCS ETLDRV capture is mapped.

No Windows bridge, Flask, Python, or ETLDRV is required by the Android transport.

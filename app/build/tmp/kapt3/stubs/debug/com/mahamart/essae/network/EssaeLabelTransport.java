package com.mahamart.essae.network;

/**
 * Direct TCP transport for the Essae-Teraoka Label Design Format observed in
 * successful ETLDRV captures.
 *
 * This is intentionally separate from EssaeTransport so the already-working
 * PLU upload implementation is not changed.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0012\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0005\n\u0002\b\b\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000b\u0018\u0000 \'2\u00020\u0001:\u0001\'B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0004H\u0002J\u0010\u0010\u0006\u001a\u00020\u00042\u0006\u0010\u0007\u001a\u00020\bH\u0002J(\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00042\u0006\u0010\u0010\u001a\u00020\u0004H\u0002J(\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00122\u0006\u0010\u0014\u001a\u00020\u00122\u0006\u0010\u0015\u001a\u00020\u00122\u0006\u0010\u0016\u001a\u00020\u0012H\u0002J\u0010\u0010\u0017\u001a\u00020\u00042\u0006\u0010\u0018\u001a\u00020\bH\u0002J\u0018\u0010\u0019\u001a\u00020\u00042\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u001a\u001a\u00020\u001bH\u0002J,\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\b0\u001d2\u0006\u0010\u001e\u001a\u00020\b2\u0006\u0010\u001f\u001a\u00020\u001bH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b \u0010!J>\u0010\"\u001a\b\u0012\u0004\u0012\u00020\b0\u001d2\u0006\u0010\u001e\u001a\u00020\b2\u0006\u0010\u001f\u001a\u00020\u001b2\u0006\u0010#\u001a\u00020\u00042\b\b\u0002\u0010\u0007\u001a\u00020\bH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b$\u0010%J\f\u0010&\u001a\u00020\b*\u00020\u0004H\u0002\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006("}, d2 = {"Lcom/mahamart/essae/network/EssaeLabelTransport;", "", "()V", "buildLabelDesignFrame", "", "design", "buildLabelFileOpenFrame", "scaleFileName", "", "exchange", "", "output", "Ljava/io/OutputStream;", "input", "Ljava/io/InputStream;", "request", "expected", "headerChecksum", "", "a", "b", "c", "d", "hex", "value", "readExact", "count", "", "testConnection", "Lkotlin/Result;", "host", "port", "testConnection-0E7RQCE", "(Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "uploadLabelDesign", "labelDesignBytes", "uploadLabelDesign-yxL6bBk", "(Ljava/lang/String;I[BLjava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "toHex", "Companion", "app_debug"})
public final class EssaeLabelTransport {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    @org.jetbrains.annotations.NotNull()
    public static final com.mahamart.essae.network.EssaeLabelTransport.Companion Companion = null;
    
    public EssaeLabelTransport() {
        super();
    }
    
    private final byte[] buildLabelFileOpenFrame(java.lang.String scaleFileName) {
        return null;
    }
    
    private final byte[] buildLabelDesignFrame(byte[] design) {
        return null;
    }
    
    private final byte headerChecksum(byte a, byte b, byte c, byte d) {
        return 0;
    }
    
    private final void exchange(java.io.OutputStream output, java.io.InputStream input, byte[] request, byte[] expected) {
    }
    
    private final byte[] readExact(java.io.InputStream input, int count) {
        return null;
    }
    
    private final byte[] hex(java.lang.String value) {
        return null;
    }
    
    private final java.lang.String toHex(byte[] $this$toHex) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/mahamart/essae/network/EssaeLabelTransport$Companion;", "", "()V", "CONNECT_TIMEOUT_MS", "", "READ_TIMEOUT_MS", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}
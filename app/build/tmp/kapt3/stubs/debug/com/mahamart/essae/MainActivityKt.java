package com.mahamart.essae;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000,\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\u001a\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0001H\u0003\u001a\u0010\u0010\u0005\u001a\u00020\u00032\u0006\u0010\u0006\u001a\u00020\u0001H\u0003\u001a\u0010\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\tH\u0007\u001a,\u0010\n\u001a\u00020\u00032\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0012\u0010\u000f\u001a\u000e\u0012\u0004\u0012\u00020\u0001\u0012\u0004\u0012\u00020\u00030\u0010H\u0007\u001a\u0018\u0010\u0011\u001a\u00020\u00032\u0006\u0010\u0012\u001a\u00020\u00012\u0006\u0010\u0013\u001a\u00020\u0001H\u0003\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2 = {"CLEAR_PLU_PIN", "", "ControlStatus", "", "text", "EmptyPanel", "message", "EssaeApp", "db", "Lcom/mahamart/essae/data/AppDatabase;", "PluCard", "plu", "Lcom/mahamart/essae/data/Plu;", "priceChanged", "", "onSavePrice", "Lkotlin/Function1;", "ScaleSectionTitle", "title", "subtitle", "app_debug"})
public final class MainActivityKt {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String CLEAR_PLU_PIN = "3331";
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable()
    public static final void EssaeApp(@org.jetbrains.annotations.NotNull()
    com.mahamart.essae.data.AppDatabase db) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ScaleSectionTitle(java.lang.String title, java.lang.String subtitle) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void ControlStatus(java.lang.String text) {
    }
    
    @androidx.compose.runtime.Composable()
    private static final void EmptyPanel(java.lang.String message) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void PluCard(@org.jetbrains.annotations.NotNull()
    com.mahamart.essae.data.Plu plu, boolean priceChanged, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.String, kotlin.Unit> onSavePrice) {
    }
}
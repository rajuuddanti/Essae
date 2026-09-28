# Label Design — Store Name Checkpoint (2026-09-28)

## Current implementation

The Label Design screen supports two bundled Essae label designs:

- **Weight Only**
- **Weight + ₹ Price**

A store-name editor is available from the top-right action in Label Design.

The saved label text is persisted locally and is applied to both label-design files before upload.

## Verified LFT format

The emulator was inspected through Android Studio Device Explorer.

The generated files are located under:

`/data/data/com.mahamart.essae/files/label_designs/`

and include:

- `weight_only.LFT`
- `weight_price.LFT`

Android Studio displays the files as readable **Essae-Teraoka Label Design Format** text.

The files contain plain text fields such as the store label name. The emulator test showed the store name being replaced inside both generated LFT files.

Example observed content:

`MAHA MART KAPUWADA`

This confirms that the current UTF-8 text replacement approach matches the observed LFT format for these bundled designs.

## Important separation

The original bundled templates under:

`app/src/main/assets/`

remain unchanged.

The runtime flow is:

`assets template → LabelDesignStore → /files/label_designs/ → store-name replacement → Essae upload`

Therefore the project assets remain the master templates while the runtime copies can carry the store-specific label name.

## Current Android implementation

Relevant files:

- `LabelDesignActivity.kt`
- `LabelDesignStore.kt`
- `ic_label_store_name.xml`
- `app/src/main/assets/Weight Only.LFT`
- `app/src/main/assets/Weight + ₹ Price.LFT`

The label-name UI asks for the exact text to print on both designs and saves it locally.

## Validation performed

- Opened Device Explorer on the Pixel 7 emulator.
- Navigated to `com.mahamart.essae/files/label_designs/`.
- Opened both generated LFT files.
- Confirmed readable Essae-Teraoka Label Design Format content.
- Confirmed the store-name field changes in the generated files.

## Safety note

Do not manually edit the generated LFT files in Device Explorer during normal operation. The app should generate them from the bundled templates.

Before any future redesign of label-file handling, preserve the observed plain-text LFT structure and test both label slots again.

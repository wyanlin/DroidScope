Android Version: 14
API Level: 34
Build Fingerprint: OnePlus/OnePlus9R_IND/OnePlus9R:14/UKQ1.230924.001/R.1a6672a-1faa-4c13c:user/release-keys
Device Serial: bc22c05f
Captured: 2026-09-10 Asia/Shanghai
Commands:
adb -s bc22c05f shell dumpsys window
adb -s bc22c05f shell dumpsys activity activities
adb -s bc22c05f exec-out dumpsys SurfaceFlinger --proto

Task 0 conclusion:
- The proto starts with the binary LYRTRACE envelope and contains nested trace entries and layers.
- Layer names use a runtime prefix and #layerId suffix, for example `1e1de1f com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher#92`.
- The fixture exposes layer id, name, type, hierarchy-related fields, layer stack, Z, bounds, and buffer-related fields.
- Window title is not byte-for-byte equal to the prefixed container Layer.name. The prefixed container layer does not carry ownerPid or windowType metadata.
- The UID-like field on the prefixed container Launcher layer is 1000, while the corresponding Window/Activity UID is 10171; it is not a safe strict relation key. The direct buffer child carries the standard metadata keys and matching values.

Approved normalization check:
- Only a suffix matching `#<decimal Layer.id>` is removed.
- For Layer 92, canonicalName is `1e1de1f com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher`, so it does not match the Window title because the runtime prefix remains.
- For its direct buffer child Layer 5635, canonicalName is `com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher`, which matches the Window title exactly.

Task 0 metadata findings:
- Layer 92 metadata contains only key 9 with bytes `e8030000` (little-endian int32 1000); keys 1, 2, and 6 are absent. It has no active buffer and its parent is 91; child 5635 is present.
- Layer 5635 metadata contains key 1 `bb270000` (OWNER_UID 10171), key 2 `01000000` (WINDOW_TYPE 1), and key 6 `eb0a0000` (OWNER_PID 2795). Values were decoded as Parcel little-endian int32, not protobuf varints. It has an active buffer and parent 92.
- Layer 5635 InputWindowInfo has layout_params_type 2 and frame [0,0][1080,2400]. This is supporting evidence only; Window type comparison uses metadata key 2.
- Layer 4443 (Settings) and Layer 5516 (DocumentsUI) have no metadata keys 1, 2, or 6 and no active buffer in this fixture.
- Canonical-name duplicates exist for generic layers such as `Leaf:0:1`, `DefaultTaskDisplayArea`, `ImeContainer`, `Dim layer`, and overlay layers. No duplicate canonical candidate was found for the Launcher, Settings, or DocumentsUI component names.
- Across the first and second refresh captures, the Launcher prefix remained `1e1de1f` and the DocumentsUI prefix remained `f12936e`; the Settings layer was absent in the second capture. This confirms the observed format is a lowercase hexadecimal token followed by one space for these samples, but does not prove token stability across all refreshes.

Window/Layer comparison — Launcher:
- Window: title `com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher`; pid 2795; uid 10171; type BASE_APPLICATION (1).
- Candidate Layer: id 5635; rawName `com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher#5635`; canonicalName `com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher`; type `Layer`; parentId 92; hasBuffer true.
- LayerProto owner_uid: metadata key 1 = 10171.
- Metadata[1] OWNER_UID: 10171.
- Metadata[2] WINDOW_TYPE: 1.
- Metadata[6] OWNER_PID: 2795.
- InputWindowInfo.layout_params_type: 2.
- InputWindowInfo.frame: [0,0][1080,2400].
- Comparison: canonicalName matches true; ownerUid matches true; ownerPid matches true; windowType matches true.

Window/Layer comparison — Settings:
- Window: title `com.android.settings/com.android.settings.Settings$UsbDetailsActivity`; pid 30838; uid 1000; type BASE_APPLICATION (1).
- Candidate Layer: id 4443; rawName `6bd7698 com.android.settings/com.android.settings.Settings$UsbDetailsActivity#4443`; canonicalName `6bd7698 com.android.settings/com.android.settings.Settings$UsbDetailsActivity`; type `Layer`; parentId 4435; hasBuffer false.
- Metadata[1] OWNER_UID: absent; Metadata[2] WINDOW_TYPE: absent; Metadata[6] OWNER_PID: absent; InputWindowInfo: absent.
- Comparison: canonicalName matches false; ownerUid／ownerPid／windowType unavailable.

Window/Layer comparison — DocumentsUI:
- Window: title `com.android.documentsui/com.android.documentsui.files.FilesActivity`; pid 6960; uid 10106; type BASE_APPLICATION (1).
- Candidate Layer: id 5516; rawName `f12936e com.android.documentsui/com.android.documentsui.files.FilesActivity#5516`; canonicalName `f12936e com.android.documentsui/com.android.documentsui.files.FilesActivity`; type `Layer`; parentId 5509; hasBuffer false.
- Metadata[1] OWNER_UID: absent; Metadata[2] WINDOW_TYPE: absent; Metadata[6] OWNER_PID: absent; InputWindowInfo: absent.
- Comparison: canonicalName matches false; ownerUid／ownerPid／windowType unavailable.

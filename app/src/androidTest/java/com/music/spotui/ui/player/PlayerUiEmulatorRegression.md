# Player UI emulator regressions

Automated label and narrow-layout checks (from `base`, on a connected emulator):

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.music.spotui.ui.player.PlayerUiRegressionTest
```

Use a signed-in debug app on the emulator, with an enabled audio-source extension and a track available from both the extension and Spotify. Turn on the source line in player preferences. Repeat the source and download cases in Classic and Immersive.

## Switch to Spotify without losing the source picker

1. Start the track from the extension. Open **Sound quality** by tapping its source line, choose **Spotify**, and dismiss the sheet.
2. As Spotify takes over, verify that its source label is already tappable before codec/bitrate arrives. If the backend does report quality, it can enrich the label without replacing the tap target. A stream that never reports bitrate must still have a Spotify label.
3. Tap the source again and switch back to the extension or select **Back to my order** if present. Long-press the source to open **Stats for nerds**.
4. Repeat while paused. Until the handoff actually occurs, the row must not misidentify the extension stream as Spotify. Repeat with the source-line preference off and confirm no source row is displayed.

## Synced lyric line and capsule

1. With **Lyrics preview in the player** enabled, play a track with known synced lyrics. In both looks, in portrait and landscape, confirm the active lyric is above the title, not between the title and seek bar. Tap it to open lyrics. Close lyrics, open the queue and check the Immersive collapsed header remains clear, then close the queue and confirm the line returns. Check instrumental/unsynced sections and the preview preference off.
2. In Immersive with **Capsule in the player** on, open the queue (or turn on the always-show playback modes setting) to reveal the capsule. Compare portrait screenshots with the volume bar on and off: the action row should remain at the bottom, not be displaced upward by a blank volume-sized spacer. Verify the queue slide and the shuffle, repeat, and device actions still work. Repeat in landscape; there should be no phantom volume row in a short window.

## Downloaded long-source fixture

- Track title: `Boys Don't Cry`
- Source text: `Downloaded · M4A · YouTube Music (AAC) · YouTube`
- Example time labels: `1:23` and `-2:34` (Immersive); `1:23` and `3:57` (Classic)
- Window/font pairings: 320dp wide at normal font size, 320dp wide at largest font size, and landscape at largest font size.

Open the downloaded track and seek while watching both time labels. The source must not overlay, shrink or ellipsize either time. On narrow layouts the source may ellipsize, but tapping it must open **Sound quality**, which must show the complete source text (wrap if needed). Long-press still opens **Stats for nerds**. Turning off the source line should hide only that entry point. Do not change download source/provider or account to make the text shorter.

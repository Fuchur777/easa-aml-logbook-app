# Play Store listing assets

None of this comes from the app bundle. Play reads the store listing's artwork only from
what is uploaded in Play Console — the launcher icon inside the AAB is what appears on the
device home screen and nowhere else. An app with a perfectly good launcher icon still shows
a blank placeholder in the store until these are uploaded.

| Asset | Requirement | Status |
| --- | --- | --- |
| App icon | 512 × 512 PNG, 32-bit, ≤ 1 MB, no transparency | `play-icon-512.png` |
| Feature graphic | 1024 × 500 PNG or JPG, no transparency | not made |
| Phone screenshots | at least 2, 16:9 or 9:16, 320–3840 px per side | not made |
| Short description | ≤ 80 characters | not written |
| Full description | ≤ 4000 characters | not written |

`play-icon-512.png` is generated, not drawn: the adaptive icon's own layers composited at
512 — `@color/ic_launcher_background` (#176FC1) under `mipmap-xxxhdpi/ic_launcher_foreground`.
So it matches the home-screen icon exactly, which is what Play expects. Regenerate it if
the launcher icon ever changes; the foreground source is 432 px, so it is a 1.19× upscale
rather than a true-resolution render.

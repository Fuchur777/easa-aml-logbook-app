# Play Store listing assets

None of this comes from the app bundle. Play reads the store listing's artwork only from
what is uploaded in Play Console — the launcher icon inside the AAB is what appears on the
device home screen and nowhere else. An app with a perfectly good launcher icon still shows
a blank placeholder in the store until these are uploaded.

| Asset | Requirement | Status |
| --- | --- | --- |
| App icon | 512 × 512 PNG, 32-bit, ≤ 1 MB, no transparency | `play-icon-512.png` |
| Feature graphic | 1024 × 500 PNG or JPG, no transparency | `play-feature-graphic-1024x500.png` |
| Phone screenshots | at least 2, 16:9 or 9:16, 320–3840 px per side | not made |
| Short description | ≤ 80 characters | `listing.md` — 71 used |
| Full description | ≤ 4000 characters | `listing.md` — 2999 used |

`play-icon-512.png` is generated, not drawn: the adaptive icon's own layers composited at
512 — `@color/ic_launcher_background` (#176FC1) under `mipmap-xxxhdpi/ic_launcher_foreground`.
So it matches the home-screen icon exactly, which is what Play expects. Regenerate it if
the launcher icon ever changes; the foreground source is 432 px, so it is a 1.19× upscale
rather than a true-resolution render.

`play-feature-graphic-1024x500.png` is generated too — the same launcher mark on the brand
blue, with the wordmark and one line of what the app is. The renderer shrinks the tagline
until it fits the available width rather than trusting a hardcoded point size, because the
first attempt ran off the right edge.

Play crops the feature graphic differently across surfaces and overlays a play button on it
if a promo video is ever added, so nothing important sits near an edge or dead centre.

# Photo contract, version 1

The camera mod and the Kodland Launcher share nothing but a folder and this format. Either side can
change its code freely as long as this file still holds; a change to the format is a new `version`.

## Where

`<game dir>/kodland-photos/inbox/` - the game dir is the launcher's instance folder.

## What

One pair of files per photo, with a common id `yyyyMMdd-HHmmss-<6 lowercase hex>` (the local time
of the shot):

1. `<id>.png` - the photo, 4:3, long side at most 1600 px, opaque.
2. `<id>.json` - written **last**, through `<id>.json.tmp` and a rename. A JSON file is the "ready"
   mark: the launcher takes only pairs whose JSON exists.

```json
{
  "version": 1,
  "id": "20261026-152807-94d5d0",
  "takenAt": "2026-10-26T15:28:07+03:00",
  "mc": "26.2",
  "mod": "kodland_camera@0.1.0",
  "world": { "name": "New World", "folder": "New World" },
  "dimension": "minecraft:the_nether",
  "biome": "minecraft:nether_wastes",
  "pos": { "x": 2, "y": 100, "z": 13 },
  "rotation": { "yaw": 41.2, "pitch": 28.5 },
  "width": 1280,
  "height": 960
}
```

- `takenAt` is ISO 8601 with the offset of the computer the photo was taken on; the launcher groups
  photos by the date written there, without converting.
- `world.name` / `world.folder` are `null` when the mod cannot tell (a multiplayer server gives the
  name only).
- The launcher ignores fields it does not know, and leaves a pair with a `version` newer than it
  understands in the inbox, untouched.

## Who reads it

The launcher (`packages/pack/src/photo-store.ts` in `kodland-launcher`) **moves** each finished pair
into its own folder, so photos survive "Delete game". The same example is a fixture in its tests.

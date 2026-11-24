# Zeitkapsel

`paper/archive` runs the `archive` group, the Zeitkapsel: old maps of earlier servers to walk and fly through, with no
game. Everyone arrives in a small entrance hall that `EntranceHall` builds into an empty world at every start. Production
runs it like a released game, as an on-demand group; see
[Game servers on demand](../../README.md#game-servers-on-demand).

## Exhibits

Exhibits live in their own template folder beside the archive's map bundle, one directory per exhibit under
`worlds/archive-exhibits/<key>/` with its `exhibit.yml` and `world/`
([layout](../../worlds/README.md#zeitkapsel-exhibits)). The folder is mounted at `exhibits.directory`, and neither a start
nor `/map save` copies it.

An exhibit's world is copied out and loaded as `exhibit:<key>` when its first visitor travels there. It is dropped again
once nobody has been in it for `exhibits.unload-after-seconds`.

Standing in an exhibit:

- `/setup spawn` sets its arrival.
- `/setup point add <name>` places a checkpoint and asks for its description in a dialog. The same name again moves and
  rewrites it.
- `/setup point remove <name>` takes one away.

Each lands in the exhibit's `exhibit.yml` at once. The `figures` there stand in the exhibit as Mannequins in their old
skins while it is loaded.

## The guide

`guide.collection-order` in the archive's `config.yml` orders collections by their exact `origin`, and other origins
follow alphabetically. `guide.suggested-visits` lists exhibit keys in display order, and missing exhibits are omitted.

## Notes

Staff with `blockcraft.archive.note` leave a note where they stand with `/archive note` or the note item in the hotbar.
Notes are rows of `bc_archive_notes`.

Notes that belong to a map itself, such as what its old NPCs did, live in its `exhibit.yml` instead
([layout](../../worlds/README.md#zeitkapsel-exhibits)). They come back with the map on any server and database, and are
edited only in the file.

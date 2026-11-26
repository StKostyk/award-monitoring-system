"""Literal search and replace across files, for edits that shell quoting would mangle.

The old and new text come from files (--old-file/--new-file) or arguments, are taken literally (no regex, no
escape processing) and keep the line endings of each target file. Every file must contain the old text exactly
--count times (default: at least once); otherwise nothing is written.

    python tools/subst.py --old-file build/old.txt --new-file build/new.txt docs/a.md docs/b.md
    python tools/subst.py --old "Sprint 6" --new "Sprint 7" --count 1 docs/project-management/BACKLOG.md
    python tools/subst.py --old "foo" --new "bar" --dry-run frontend/src/app/x.ts
"""

import argparse
import pathlib
import sys


def read_text(value, path):
    if path:
        return pathlib.Path(path).read_text(encoding="utf-8").removesuffix("\n").replace("\r\n", "\n")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--old")
    parser.add_argument("--old-file")
    parser.add_argument("--new")
    parser.add_argument("--new-file")
    parser.add_argument("--count", type=int, help="exact number of occurrences expected in each file")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("files", nargs="+")
    args = parser.parse_args()

    old = read_text(args.old, args.old_file)
    new = read_text(args.new, args.new_file)
    if old is None or new is None:
        parser.error("give --old or --old-file, and --new or --new-file")
    if not old:
        parser.error("the old text is empty")

    changes = []
    for name in args.files:
        path = pathlib.Path(name)
        raw = path.read_bytes().decode("utf-8")
        eol = "\r\n" if "\r\n" in raw else "\n"
        text = raw.replace("\r\n", "\n")
        found = text.count(old)
        if found == 0 or (args.count is not None and found != args.count):
            expected = args.count if args.count is not None else "at least 1"
            print(f"{name}: {found} occurrence(s), expected {expected}; nothing written", file=sys.stderr)
            return 1
        changes.append((path, text.replace(old, new).replace("\n", eol), found))

    for path, text, found in changes:
        if not args.dry_run:
            path.write_bytes(text.encode("utf-8"))
        print(f"{path}: {found} replaced{' (dry run)' if args.dry_run else ''}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

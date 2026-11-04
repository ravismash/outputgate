# Publishing the action to GitHub Marketplace

This folder is the action as it will live in its own repository. Marketplace requires a public
repository with a single `action.yml` at its root, so the action can't be listed from the main
outputgate repository.

1. **Release outputgate with the CLI jar.** Tag the next version (e.g. `v0.2.0`). The release
   workflow now attaches `outputgate-cli-<version>.jar` and its `.sha256` to the GitHub release.
   If the version isn't `0.2.0`, change the `version` default in `action.yml` and `README.md`.
2. **Create the public repository** `ravismash/outputgate-action` and copy `action.yml`,
   `README.md` and `LICENSE` (MIT, from the main repo) to its root. Leave out `example/` and
   this file; add a workflow that runs the action on `example/` if you want one there too.
3. **Account prerequisites** (once): two-factor authentication on the account, and accept the
   GitHub Marketplace Developer Agreement (GitHub asks on the first publish).
4. **Publish.** On the new repository, Releases > Draft a new release, tick "Publish this Action
   to the GitHub Marketplace", choose a primary category (Testing) and a second one (e.g.
   Continuous integration or AI-assisted), tag `v1.0.0`, and publish. GitHub checks that the
   `name` in `action.yml` is unique and that `description` and `branding` are set.
5. **Move the major tag** so `@v1` works: `git tag -f v1 v1.0.0 && git push -f origin v1`. Do this
   again on each `v1.x.y` release.

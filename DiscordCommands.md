# Discord commands

This page describes the Discord commands that are available in EpiLink.

?> Discord commands are available since version 0.4.0

## Prerequisites

In order to be able to use commands, you must:

- Be an [admin](MaintainerGuide.md#general-settings) in the config file
- Be registered in EpiLink with your identity recorded
- Use the prefix you set in the config file (`e!` by default, e.g. `e!update`)
- The server you're running the command on must be [monitored](MaintainerGuide.md#discord-configuration), i.e. it must be configured in EpiLink's config file.

## User target

A user target (identified by `<user>` in the syntax examples of the commands below) is a target that is used by EpiLink to understand what user you meant in your reply. User targets can take different forms:

- **For a single user**
    - `<@12345>`, a regular Discord ping (just @ the person within Discord)
    - `12345`, their Discord ID directly. This is useful for updating someone without pinging them.
- **For users that have a specific role**
    - `<@#56789>`, a regular Discord role ping (just @ the role within Discord)
    - `/56789`, the role's Discord ID with a / at the beginning. This is useful for updating everyone that has a specific role without pinging them.
    - `|Role Name`, the role's exact name with a | at the beginning. This is useful when you want to save the 5 seconds it would take to get the role's ID 😉
- **For everyone**
    - `!everyone`. This is the only way of updating everyone, pinging `@everyone` will NOT WORK by design, because nobody wants to ping everyone for a role update.

## Commands

Here are the available commands:

### update

The `update` command can be used to refresh the roles of a single user or multiple users. The roles are updated *globally*, meaning that refreshing someone will refresh their roles on every server they're on, not just the server they're in at the moment. This is by design to avoid discrepancies between the roles on the servers.

The syntax is:

```
update <user>
```

Where `<user>` is a [user target](#user-target). Note that you must prepend the command with the prefix: this would give `e!update <user>` if you're using the default prefix.

### help

The `help` commands displays a help message that redirects you to this very page. It takes no arguments, so simply call it with `e!help`. Replace `e!` with your the prefix you set in the configuration. The default prefix is `e!`.
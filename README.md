# Pixelball

Pixelball is a server-side Fabric Minecraft mod that connects a Tiltify campaign to in-game donation rewards, donation goals, and a bossbar showing campaign progress.

## Requirements

| Requirement   | Version        |
|---------------|----------------|
| Minecraft     | 1.21.1         |
| Fabric Loader | 0.16.14+       |
| Fabric API    | 0.116.4+1.21.1 |
| Java / JDK    | 21             |

## Installation

1. Create a Fabric Loader (Server) and add [Fabric API](https://modrinth.com/mod/fabric-api) to `mods`.
2. Put the Pixelball jar in the server `mods` folder.
3. Start the server once to generate the config.
4. Edit: `config/pixelball/config.yml`
5. Run `/pixelball reload` to load the config and start the donation bar or restart server.


## Tiltify Setup

Pixelball uses Tiltify API credentials to read campaign donation data.

1. Go to the [Tiltify Developer Dashboard](https://app.tiltify.com/developers).
2. Create an OAuth application.
   - App name: 'Pixelball S#'.
   - Redirect URI can be `http://localhost` (It's not used).
3. Copy your `Client ID` and `Client Secret`.
4. Use your Tiltify campaign ID.
   - [Teams](https://app.tiltify.com/teams) (or [Pixelball Team]([https://app.tiltify.com/teams/8ab2a22e-972a-4f85-9f2d-7c814cbd219e/campaigns]) for Pixelball)
   - Click `View Campaigns` on your team.
   - Click the campaign.
   - Click Setup on the left, Then the Information tab at the end
   - Copy the `ID`
5. Put those values in `config/pixelball/config.yml`.

Tiltify uses OAuth 2.0 access tokens. For server-side integrations like this, Tiltify recommends the Client Credentials flow, using `client_id`, `client_secret`, `grant_type: client_credentials`, and `scope: public`.

## Config Defaults
View [config.yml](src/main/resources/config.yml)


## Commands

| Command                                                | Description                                          | Permission                  |
|--------------------------------------------------------|------------------------------------------------------|-----------------------------|
| `/pixelball`                                           | Display the donation link and current amount raised. | None                        |
| `/pixelball donate`                                    | Display the donation link and current amount raised. | None                        |
| `/pixelball help`                                      | Display the command help menu.                       | None                        |
| `/pixelball reload`                                    | Reload the plugin configuration and donation bar.    | `pixelball.reload`          |
| `/pixelball action random_pokeball`                    | Test the random Poké Ball reward.                    | `pixelball.action`          |
| `/pixelball action random_stone`                       | Test the random Evolution Stone reward.              | `pixelball.action`          |
| `/pixelball action random_held_item`                   | Test the random held item reward.                    | `pixelball.action`          |
| `/pixelball action legendaryspawn <count>`             | Test the legendary spawn reward.                     | `pixelball.action`          |
| `/pixelball action enable_nether`                      | Test the Nether unlock reward.                       | `pixelball.action`          |
| `/pixelball action enable_end`                         | Test the End unlock reward.                          | `pixelball.action`          |
| `/pixelball bypass nether <player>`                    | Allow a player to bypass the Nether restriction.     | `pixelball.bypass`          |
| `/pixelball bypass end <player>`                       | Allow a player to bypass the End restriction.        | `pixelball.bypass`          |
| `/pixelball debug <player>`                            | Toggle legendary spawn debug messages for a player.  | `pixelball.debug`           |
| `/pixelball donate raised set <amount>`                | Override the displayed donation total.               | `pixelball.donate.raised`   |
| `/pixelball donate simulate <user> <amount> <message>` | Simulate a donation event.                           | `pixelball.donate.simulate` |


## Building
CLI build with Gradle:
```bash
./gradlew build
```

The built jar will be in:
```txt
build/libs/
```

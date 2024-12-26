# Resourceloader

**Resourceloader** is a simple, flexible plugin for Minecraft servers that allows for seamless management of resource packs without requiring players to endure the default server resource pack loading process. The plugin supports multiple resource packs, enabling players to load specific packs on demand via commands.

## Key Features

- **Simplified Resource Pack Loading:** Avoid the default server-side resource pack loading experience, which can be confusing or annoying for new players.
- **Dynamic Resource Pack Selection:** Load specific resource packs as needed using commands.
- **Customizable Configurations:** Define and manage multiple resource packs via the `config.yml` file.

## Commands

### `/load [packname]`
- **Description:** Loads a resource pack for the player.
- **Usage:**
    - `/load` - Loads the default server pack.
    - `/load <packname>` - Loads a specific resource pack by name.
- **Permission:** `resourceloader.load`

### `/listpacks`
- **Description:** Lists all available resource packs.
- **Usage:** `/listpacks`
- **Permission:** `resourceloader.list`

### `/resourcereload`
- **Description:** Reloads the plugin configuration.
- **Usage:** `/resourcereload`
- **Permission:** `resourceloader.reload`

## Permissions

- **`resourceloader.load`**
    - Allows players to load resource packs.
    - Default: `true`
- **`resourceloader.list`**
    - Allows players to list available resource packs.
    - Default: `true`
- **`resourceloader.reload`**
    - Allows operators to reload the plugin configuration.
    - Default: `op`

## Configuration (`config.yml`)

```yaml
# Main server resource pack URL
# This pack is loaded when using /load without arguments
server-pack: "https://example.com/server-pack.zip"

# Additional resource packs
# These packs can be loaded using /load <packname>
# To add a new pack, use a key:"value" format
resource-packs:
  example: "https://example.com/dungeons-pack.zip"
  example0: "https://example.com/example0.zip"
  example1: "https://example.com/example1.zip"
```

### Adding New Resource Packs
1. Open `config.yml`.
2. Add a new entry under `resource-packs` in the format `key: "URL"`.
3. Save the file and run `/resourcereload` to apply the changes.

## How It Works

1. Players can use `/load` to load the default resource pack or a specific pack by name.
2. Use `/listpacks` to see all available resource packs.
3. Administrators can reload the plugin configuration using `/resourcereload`.

## Example

- **Loading Default Pack:**
  ```
  /load
  ```

- **Loading a Specific Pack:**
  ```
  /load example
  ```

- **Listing Available Packs:**
  ```
  /listpacks
  ```

- **Reloading Configuration:**
  ```
  /resourcereload
  ```

## Installation

1. Download the plugin JAR file.
2. Place it in the `plugins` folder of your Minecraft server.
3. Start the server to generate the default configuration file.
4. Customize `config.yml` as needed.
5. Use `/resourcereload` to apply your changes.

## Requirements

- Minecraft Server Version: `1.13` or higher
- Java Version: `17` or higher

## Credits

**Author:** DefectiveVortex  
**Website:** [GitHub Repository](https://github.com/DefectiveVortex/Resourceloader)

---

Feel free to contribute or suggest improvements via the [GitHub repository](https://github.com/DefectiveVortex/Resourceloader).
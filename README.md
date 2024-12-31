# Resourceloader

A simple and flexible resource pack loader and merger plugin for Minecraft servers. Load different resource packs and merge multiple packs into one with ResourceLoader!

## Features

- Load the server's default resource pack with a simple command
- Configure and load multiple additional resource packs
- Merge multiple resource packs in the plugin itself
- Support for both URL and file-based resource packs
- Easy-to-use commands
- Permission-based access control
- Simple configuration

## Commands

- `/load` - Load the default server resource pack
- `/load <packname>` - Load a specific resource pack
- `/mergepack <output-file>.zip <pack1> <pack2> [pack3...]` - Merge existing resource packs in the plugin itself
- `/listpacks` - List all available resource packs
- `/resourcereload` - Reload the plugin configuration
- `/resourceversion` - Check the version of ResourceLoader you're running

## Permissions

- `resourceloader.load` - Allows using the /load command (default: true)
- `resourceloader.list` - Allows using the /listpacks command (default: true)
- `resourceloader.reload` - Allows reloading the plugin configuration (default: op)
- `resourceloader.admin` - Allows access to all the admin commands of ResourceLoader (default: op)
- 
## Configuration

```yaml
# Main server resource pack
# Can be either a URL or a file name in the packs folder
# For URLs: "https://example.com/server-pack.zip"
# For files: "server-pack.zip"
server-pack: "server-pack.zip"

# Web server settings
server-port: 40021  # Default port, can be changed to any open port on your host
localhost: false    # Set to false for production servers, true only for local testing
server-address: ""  # Will try to auto-detect if empty
fallback-address: "localhost"  # Used if auto-detection fails

# Additional resource packs
# Can use either URLs or files from the packs folder
# For URLs: "https://example.com/examplepack.zip"
# For files: "example-pack.zip"
resource-packs:
  exampleurlpack: "https://example.com/examplepack.zip"
  examplefilepack: "example-pack.zip"
```

## Installation

1. Download the latest release
2. Place the JAR file in your server's `plugins` folder
3. Start/restart your server
4. A `packs` folder will be created in `plugins/Resourceloader/`
5. Place your resource pack files in the `packs` folder
6. Configure the packs in `config.yml`
7. Use `/resourcereload` to apply changes

## File-based Resource Packs

To use file-based resource packs:
1. Place your .zip resource pack files in the `plugins/Resourceloader/packs/` folder
2. In config.yml, use just the filename (e.g., "my-pack.zip") instead of a full URL
3. The plugin will automatically look for the file in the packs folder

## URL-based Resource Packs

For URL-based packs:
1. Use the complete URL in config.yml (e.g., "https://example.com/pack.zip")
2. Ensure the URL is directly downloadable
3. The URL must point to a valid Minecraft resource pack .zip file

## Requirements

- Minecraft 1.13 or newer
- Spigot/Paper server

## Support

If you encounter any issues or have suggestions, please create an issue on the GitHub repository.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
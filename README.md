# Resourceloader

A powerful and flexible resource pack management plugin for Minecraft servers. Manage, merge, and distribute resource packs with advanced features for optimization and user experience!

## Features

- Advanced resource pack management system with automatic backups and statistics
- Smart compression system with adaptive compression levels based on player connection
- Merge multiple resource packs with conflict resolution and preview options
- Support for both URL and file-based resource packs
- Automatic resource pack enforcement with customizable actions
- Automatic sync between packs folder and configuration
- Performance optimizations with caching and compression
- GUI interface for pack merging
- Easy-to-use commands with tab completion
- Permission-based access control
- Comprehensive configuration options

## Commands

- `/load` - Load the default server resource pack
- `/load <packname>` - Load a specific resource pack
- `/mergepack <output>.zip <pack1> <pack2> [pack3...]` - Merge resource packs
- `/mergegui` - Open the graphical interface for merging packs
- `/removepack <packname>` - Remove a resource pack
- `/listpacks` - List all available resource packs
- `/resourcereload` - Reload the plugin configuration
- `/resourceversion` - Check for plugin updates
- `/clearcache` - Clear the resource pack cache

## Permissions

- `resourceloader.load` - Use the /load command (default: true)
- `resourceloader.list` - Use the /listpacks command (default: true)
- `resourceloader.admin` - Access to all admin commands (default: op)
- `resourceloader.bypass` - Bypass forced resource pack loading (default: op)

## Configuration

```yaml
# ResourceLoader Configuration

#====================#
# Basic Settings    #
#====================#

# The default server resource pack
server-pack: "server-pack.zip"

#====================#
# Enforcement       #
#====================#

# Resource pack enforcement settings
enforcement:
  enabled: false      # Should players be required to accept resource packs?
  action: kick        # What to do when a player declines (kick, restrict)
  decline-message: "You must accept the resource pack to play on this server!"
  failed-action: kick # What to do when download fails (kick, restrict)
  failed-message: "Failed to download the resource pack. Please try joining again!"

#====================#
# Server Settings   #
#====================#

# Web server configuration
server:
  port: 40021          # The port to serve resource packs on
  localhost: false     # Set to true only for local testing
  address: ""          # Leave empty for auto-detection
  fallback: localhost  # Used if auto-detection fails

#====================#
# Performance       #
#====================#

# Resource pack compression
compression:
  enabled: true
  auto-select: true    # Automatically choose compression based on player's connection
  default: medium      # Used when auto-select is false (options: low, medium, high)

# Caching settings
cache:
  enabled: true
  days: 7             # How long to keep cached packs
  auto-cleanup: true  # Remove old cache entries automatically

#====================#
# Backup & Stats    #
#====================#

# Automatic backup settings
backup:
  enabled: true
  interval: 24       # Hours between backups
  keep: 5           # Number of backups to keep

# Usage statistics
statistics:
  enabled: true     # Track pack usage and performance
  save-preferences: true   # Remember player's last used packs
  max-history: 5    # Number of packs to remember per player

#====================#
# Interface         #
#====================#

# GUI settings for the pack merger
gui:
  enabled: true
  preview: true     # Show pack preview before merging
  conflicts: ask    # How to handle conflicts (ask, overwrite, skip)

# Resource pack definitions
resource-packs:
  # Automatically populated based on packs in the folder
```

## Installation

1. Download the latest release
2. Place the JAR file in your server's `plugins` folder
3. Start/restart your server
4. A `packs` folder will be created in `plugins/Resourceloader/`
5. Place your resource pack files in the `packs` folder
6. Configure the plugin in `config.yml` (or let it auto-configure)
7. Use `/resourcereload` to apply changes

## Resource Pack Management

### File-based Resource Packs
- Place .zip resource packs in `plugins/Resourceloader/packs/`
- Files are automatically detected and added to configuration
- Configuration keys are generated based on filenames
- Automatic backups are created based on your settings

### URL-based Resource Packs
- Support for direct download URLs
- Automatic caching for better performance
- Smart compression based on player connection quality
- Configurable cache duration and cleanup

### Pack Merging
- Merge multiple packs using command or GUI
- Preview changes before merging
- Configurable conflict resolution
- Maintains pack format compatibility
- Automatic handling of duplicate assets

### Performance Features
- Adaptive compression based on player connection
- Intelligent caching system
- Automatic cleanup of old cache entries
- Optimized pack serving and delivery

### Statistics and Monitoring
- Track pack usage and performance
- Player preference history
- Backup management
- Usage statistics for optimization

## Requirements

- Minecraft 1.13 or newer
- Java 17 or newer

## Support

If you encounter any issues or have suggestions:
- Create an issue on the GitHub repository
- Contact @vortexunwanted on Discord (faster response)
- Check the wiki for detailed documentation

## License

This project is licensed under the MIT License - see the LICENSE file for details.

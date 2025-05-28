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
- `/autoload <clear/packname>` - Automatically loads in the specified pack upon joining
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
# For detailed documentation, visit: https://github.com/DefectiveVortex/Resourceloader

#====================#
# Resource Packs     #
#====================#

# The default server resource pack that will be loaded when using /load without arguments
# Examples:
#   Local file: "server-pack.zip"
#   URL: "https://example.com/server-pack.zip"
server-pack: ""

# Individual resource pack definitions
# Format:
#   packname: "filename.zip"    # For local files
#   packname: "https://..."    # For external URLs
resource-packs:
  # example1: "pack1.zip"
  # example2: "https://example.com/pack2.zip"

# Storage configuration
storage:
  # Custom directory for resource packs (absolute path)
  # Leave empty to use plugins/ResourceLoader/packs/
  resource-pack-directory: ""
  
  # Automatic resource pack detection
  auto-detection: true

#====================#
# Server Settings    #
#====================#

# Web server configuration for serving resource packs
server:
  # The port to serve resource packs on (default: 40021)
  port: 40021
  
  # Network settings (advanced)
  localhost: false     # Set to true only for local testing
  address: ""         # Leave empty for auto-detection
  fallback: localhost # Used if auto-detection fails

#====================#
# Performance       #
#====================#

# Resource pack compression settings
compression:
  # Enable compression of resource packs
  enabled: true
  
  # Compression level selection
  auto-select: true              # Choose compression based on player's connection
  default-level: "medium"        # Used when auto-select is false
                                # Options: "low", "medium", "high"

# Cache settings for downloaded resource packs
cache:
  enabled: true
  expiry-days: 7      # How long to keep cached packs
  auto-cleanup: true  # Automatically remove expired cache entries

#====================#
# Pack Management   #
#====================#

# Backup configuration
backup:
  enabled: true
  interval-hours: 24  # Hours between automatic backups
  keep-count: 5       # Number of backup files to retain

# Usage statistics and player preferences
statistics:
  enabled: true                # Track pack usage and performance
  save-preferences: true       # Remember player's last used packs
  max-preferences: 5           # Number of packs to remember per player

#====================#
# User Interface    #
#====================#

# GUI settings for the pack merger
gui:
  enabled: true
  show-preview: true          # Show pack preview before merging
  conflict-handling: "ask"    # How to handle conflicts: "ask", "overwrite", "skip"

#====================#
# Enforcement       #
#====================#

# Resource pack enforcement settings
enforcement:
  enabled: false
  kick-on-decline: true
  kick-on-fail: true
  restrict:
    prevent-interaction: true
  # Whether to use vanilla Minecraft's resource pack system by modifying server.properties
  # WARNING: This will override any existing resource pack settings in server.properties
  use-server-properties: false
  # If true, will create a public URL for the pack that doesn't require authentication
  # Only used when use-server-properties is true and a local file is used
  make-pack-public: false
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

## License

This project is licensed under the MIT License - see the LICENSE file for details.
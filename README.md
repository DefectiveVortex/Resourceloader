# Resourceloader
## Commands

### Core Commands
- `/load` - Load the default server resource pack
- `/load <packname>` - Load a specific resource pack
- `/autoload <clear/packname>` - Set automatic pack loading for players on join
- `/listpacks` - List all available resource packs with status information

### Pack Management
- `/mergepack <output>.zip <pack1> <pack2> [pack3...]` - Merge multiple resource packs with intelligent conflict resolution
- `/mergegui` - Open the graphical interface for merging packs with preview
- `/removepack <packname>` - Remove a resource pack from server and configuration
- `/checkpack <packname>` - Validate resource pack structure, JSON files, and textures

### Administration
- `/resourcereload` - Reload the plugin configuration and rescan packs folder
- `/resourceversion` - Check for plugin updates and version information
- `/clearcache` - Clear the resource pack cache and temporary filesand flexible resource pack management plugin for Minecraft servers. Manage, merge, and distribute resource packs with advanced features for optimization and user experience!

## Features

- **Advanced Resource Pack Management** - Complete pack lifecycle management with automatic backups and statistics
- **Smart Compression System** - Adaptive compression levels based on player connection quality
- **Intelligent Pack Merging** - Merge multiple packs with JSON/model conflict resolution and pack format compatibility
- **Resource Pack Validation** - Comprehensive validation system for pack structure, JSON files, and missing textures
- **Dual Pack Support** - Support for both local file and URL-based resource packs with automatic caching
- **Enhanced Enforcement** - Reliable resource pack enforcement with proper player join handling and autoload
- **Performance Optimizations** - SHA1 hash caching, intelligent compression, and optimized pack serving
- **Smart File Watching** - Event batching system prevents duplicate notifications and config conflicts
- **GUI Interface** - User-friendly graphical interface for pack merging with preview options
- **Robust Configuration** - Auto-sync between packs folder and configuration with duplicate prevention
- **Permission System** - Granular permission-based access control
- **Easy Commands** - Intuitive commands with tab completion and comprehensive help

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
- Configuration keys are generated based on filenames (sanitized to prevent conflicts)
- Automatic backups are created based on your settings
- Smart file watching prevents duplicate notifications and config corruption

### URL-based Resource Packs
- Support for direct download URLs with progress tracking
- Automatic caching with SHA1 hash verification for better performance
- Smart compression based on player connection quality
- Configurable cache duration and automatic cleanup
- Reliable download with retry mechanisms

### Resource Pack Validation
- **Comprehensive Structure Validation** - Checks for pack.mcmeta, assets folder, and proper directory structure
- **JSON File Validation** - Validates model files, blockstates, and item models for syntax errors
- **Texture Reference Checking** - Detects missing textures referenced in model files
- **Pack Format Compatibility** - Ensures pack format matches server Minecraft version
- **Use `/checkpack <packname>` to validate any resource pack**

### Enhanced Pack Merging
- **Intelligent JSON Merging** - Smart handling of model files, blockstates, and item models
- **Texture Conflict Resolution** - Preview and choose how to handle duplicate textures
- **Pack Format Auto-Detection** - Automatically sets correct pack_format for server version
- **Metadata Preservation** - Maintains pack.mcmeta compatibility and descriptions
- **Real-time Preview** - See changes before committing to merged pack
- **Command or GUI interface** - Use `/mergepack` or `/mergegui` for different workflows

### Autoload Priority System
- **Player Preference Priority** - Autoload preferences (set via `/autoload`) take priority over server enforcement
- **Smart Pack Loading** - Players with autoload preferences bypass enforcement and get their chosen pack
- **Detailed Console Logging** - Clear logs showing whether autoload or enforcement is being applied
- **Seamless Experience** - No conflicts between personal preferences and server requirements
- **Per-Player Customization** - Each player can have their own preferred pack while maintaining server defaults

### Performance & Reliability Features
- **SHA1 Hash Caching** - Prevents re-downloading unchanged packs and validates file integrity
- **Event Batching** - Prevents duplicate file system notifications and reduces server load
- **Adaptive Compression** - Optimizes pack size based on player connection quality  
- **Intelligent Enforcement** - Proper player join event handling for reliable pack loading
- **Configuration Protection** - Prevents config corruption from simultaneous modifications
- **Smart Cache Management** - Automatic cleanup of expired cache entries with configurable retention
- **Optimized Pack Serving** - Efficient file serving with minimal memory footprint

### Statistics and Monitoring
- Track pack usage and performance
- Player preference history
- Backup management
- Usage statistics for optimization

## Troubleshooting & Best Practices

### Common Issues
- **Pack Not Loading**: Use `/checkpack <packname>` to validate pack structure and identify issues
- **Config Corruption**: Plugin automatically prevents duplicate entries and config corruption
- **Performance Issues**: Enable SHA1 caching and compression for better performance
- **Player Join Issues**: Enforcement now uses PlayerJoinEvent for reliable pack loading
- **Autoload Not Working**: Check console logs for detailed autoload/enforcement messages

### Autoload vs Enforcement Priority
- **Priority Order**: Player autoload preferences (`/autoload`) always take priority over server enforcement
- **How It Works**: When a player joins, the plugin first checks for their autoload preference, then falls back to enforcement
- **Console Logging**: Look for "Autoload:" or "Enforcement:" messages in console to see which system is active
- **Player Control**: Players can use `/autoload clear` to remove their preference and fall back to server enforcement

### Best Practices
- **Validate Packs**: Always run `/checkpack` on new packs before distribution
- **Use Compression**: Enable adaptive compression for better player experience
- **Regular Maintenance**: Use `/clearcache` periodically to clear old cache files
- **Monitor Logs**: Check console for pack validation warnings and errors
- **Backup Strategy**: Enable automatic backups in configuration

### Performance Tips
- Place smaller packs in the `packs` folder for faster local serving
- Use URL-based packs for larger packs with CDN acceleration
- Enable SHA1 caching to reduce redundant downloads
- Configure appropriate cache retention periods

## Requirements

- Minecraft 1.13 or newer
- Java 17 or newer

## Support

If you encounter any issues or have suggestions:
- Create an issue on the GitHub repository
- Contact @vortexunwanted on Discord (faster response)

## License

This project is licensed under the MIT License - see the LICENSE file for details.
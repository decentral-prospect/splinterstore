# SplinterStore

SplinterStore is a decentralized app store built on the **Ouisync** network.

The project is still in **alpha**, so there might be bugs, things might break, and we're changing stuff pretty often. We're actively working on it and would love any feedback or bug reports!

### Important note
For the app catalog to show anything, this main index repository needs to be reachable:
https://ouisync.net/r#AwIgh4yZF3zfZ18seYQb320u7QNeu2fw4PAqUd9RK7gjyfc?name=index

## What's new

### Version 0.0.2
- Big overhaul of how we talk to Ouisync.  
  Switched to direct Maven dependencies for the API instead of building the whole Ouisync project from source locally. Building the app is now much faster and simpler, and it should be way easier for others to jump in and help out.

- Added app icons!  
  They're stored in a dedicated Ouisync repo just for icons and get pulled in at runtime. Now we can add or update icons without releasing a new version of SplinterStore.


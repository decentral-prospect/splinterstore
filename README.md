# SplinterStore

SplinterStore is a **decentralized app store** built on the **Ouisync** network.

The project is still in **alpha**, so:

- there might be bugs
- things might break
- we're changing stuff pretty often

We're actively working on it and would **love any feedback or bug reports**!

## Configure index & icons repositories

SplinterStore loads the app catalog (**index**) and app **icons** from Ouisync repositories.

By default the app already has built-in fallback tokens, so it can work out of the box.

If you want to use **your own** index/icons repositories  
(recommended for development), override tokens via `local.properties`.

### 1. Add tokens to `local.properties`

Create or edit file `local.properties` in the project root  
(same level as `settings.gradle`):

```
# Override Ouisync repositories used by SplinterStore (optional)
OUISYNC_INDEX_REPO_TOKEN=Your_index_token_here
OUISYNC_ICONS_REPO_TOKEN=Your_icons_token_here
```


If those values are missing or empty, SplinterStore will fall back to the **default tokens** hardcoded in the app.

### 2. What these repos are for

Index repo contains **index.json**, the catalog describing categories, apps, versions and per-app repository tokens. Icons repo contains icon files (e.g. telegram.svg, ceno.jpg, amnezia.png).

How to add/update an icon:

1. Put the icon file into the icons repo
2. In index.json set "icon": "<file_name>" (must match the filename stored in the icons repo)

### Minimal rules for `index.json`

Root object must contain "list": [ ... ]

Each entry in "list" — is a category:

  • "category" : category name (string)
  
  • "apps"     : list of apps

Each app object:

  • "name"   : display name

  • "version": display version string

  • "icon"   : icon filename that exists in the icons repo

  • "token"  : share token for the app repository (where the APK is stored)

  • "dscb"   : short description (optional)

### Example index.json:
```
{
  "list": [
    {
      "category": "Messengers",
      "apps": [
        {
          "name": "Element",
          "version": "1.0.2",
          "icon": "element.svg",
          "token": "https://ouisync.net/r#AwEgLLHnUtkG3h2aZZB1yIgNTsDv0zyiP5V--_52Moqk_R4g_vI3HJRDrqAD_yE2MegoqZP_e-NV_x8al-efxhecFU4?name=element",
          "dscb": "About it"
        },
        {
          "name": "Telegram",
          "version": "2.3.4",
          "icon": "telegram.svg",
          "token": "https://ouisync.net/r#AwEgX1tXjVXwjciVkivvDkLnCH35mlviLXUQvtK89XYvgNMgiivbd6B1WgvKlgxP8rsItqPM47qJXVrwTHU-qWL0h8I?name=tg",
          "dscb": "About it"
        },
        {
          "name": "Signal",
          "version": "2.3.4",
          "icon": "signal.svg",
          "token": "https://ouisync.net/r#AwEgG3KlHLYSvbYSGwGYJhIsIB_oThiQEiGRxuwV0tOqWwogA5MOgMZVQHeDcNgXT3CDOShaxSwodnbHAcm-8WszvH4?name=signal",
          "dscb": "About it"
        },
        {
          "name": "Session",
          "version": "2.3.4",
          "icon": "session.svg",
          "token": "https://ouisync.net/r#AwEg46Jg2adbm4Hg24pDE8vzRdvHOer5kfK3lB53BUNV2kEgy4UD_HbwWXXW89bxNaMzWmLCfYHe6njgE1cmcfXzyN4?name=session",
          "dscb": "About it"
        }
      ]
    },
    {
      "category": "Browsers",
      "apps": [
        {
          "name": "Ceno",
          "version": "1.5.0",
          "icon": "ceno.jpg",
          "token": "https://ouisync.net/r#AwEg8Fu61Bf9UoRH3p7CyldvbklZ9_kmcQgwyqsbXP84ycIgSw3q7rYnKcrJ4zljBQdM9N5jCj9wgRajK6RJE17i1dw?name=ceno",
          "dscb": "About it"
        },
        {
          "name": "Tor",
          "version": "3.2.2",
          "icon": "tor.svg",
          "token": "https://ouisync.net/r#AwEg6541NB83zykBmbmmEPc23323CmLtZe5wcItnOpl73JcgMjn6DU4_1yUkjaHFiBVtPUXsIKBmwdyRNBfzoxVqa1g?name=tor",
          "dscb": "About it"
        }
      ]
    },
    {
      "category": "VPNs",
      "apps": [
        {
          "name": "AmneziaVPN",
          "version": "1.5.0",
          "icon": "amnezia.png",
          "token": "https://ouisync.net/r#AwEg-PYagOoes8or4QUafMwSj3KUaUyGXMo-C3WLslHxluQg3fhBR-RYjuBJYO5MvKv5g00cF1kbcMSAEAM26Joaar8?name=amnezia",
          "dscb": "About it"
        }
      ]
    },
    {
      "category": "File sharing",
      "apps": [
        {
          "name": "Ouisync",
          "version": "1.5.0",
          "icon": "ouisync.png",
          "token": "https://ouisync.net/r#AwEg-YI2a1t_FOzTpxVwVDORkGBCKX30dSTYS7B7p4qmL8sgp19zBxFYsflxJQPsOyzWM7tHKYvwQlW5OZDu-UFL7pc?name=ouisync",
          "dscb": "About it"
        }
      ]
    }
  ]
}
```
### Important note
For the app catalog to show anything, the index repository must be reachable.

Default index repo: https://ouisync.net/r#AwIgh4yZF3zfZ18seYQb320u7QNeu2fw4PAqUd9RK7gjyfc?name=index

## What's new

### Version 0.0.2
- Big overhaul of how we talk to Ouisync.  
  Switched to direct Maven dependencies for the API instead of building the whole Ouisync project from source locally. Building the app is now much faster and simpler, and it should be way easier for others to jump in and help out.

- Added app icons!  
  They're stored in a dedicated Ouisync repo just for icons and get pulled in at runtime. Now we can add or update icons without releasing a new version of SplinterStore.
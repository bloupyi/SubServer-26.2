# SubServer

## About SubServer

SubServer is a Minecraft **26.2** plugin made with Paper. The plugin splits your server into instances. An instance can have multiples maps and players, like a sub-server. It uses Advanced Slime Paper to fast load maps.

## Requirements

| Component | Version |
|-----------|---------|
| Server    | Advanced Slime Paper **26.2** (ASP `4.2.0-SNAPSHOT`, branch `develop`) |
| Java      | **25** (required by Paper 26.2) |
| API       | `io.papermc.paper:paper-api:26.2.build.124-stable` |

## Depend

[Advanced Slime Paper (ASP)](https://github.com/InfernalSuite/AdvancedSlimePaper)

Get a 26.2 server jar from the InfernalSuite build API:

```bash
# latest 26.2 build, then pick the asp-server.jar file id
curl -s https://api.infernalsuite.com/v1/projects/asp/mcversion/26.2/latest
curl -L -o asp-server.jar \
  https://api.infernalsuite.com/v1/projects/asp/d1607236-69ed-4d1d-a286-62d2f0712dad/download/18d1b22c-e9a7-42ab-b790-e77d5ac95da0
```

The matching API artifacts come from `https://repo.infernalsuite.com/repository/maven-snapshots/`
(`com.infernalsuite.asp:api` and `com.infernalsuite.asp:file-loader`, version `4.2.0-SNAPSHOT`).

### Using SubServer

SubServer uses a templating system which works by registering your `InstanceType` (your template) inside an `InstanceFactory`. You have a few settings to configure inside the `InstanceType` object and most importantly, you need to set a runnable that will be executed after the initialization of each instance.
That is your entrypoint to control the behavior of instances.

Another important point is that for each instance, if you need to register an event listener, you must do it using the `Instance#registerListener` method. It will allow you to only catch the events of your own instance, you won't have to filter yourself which event is yours.

## Contributors
- [LoanSpac](https://github.com/LoanSpac)
- [Clooooud](https://github.com/Clooooud)

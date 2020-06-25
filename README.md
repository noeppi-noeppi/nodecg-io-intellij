**This project is WIP see [here](https://github.com/codeoverflow-org/nodecg-io/pull/29) for any updates)**

# nodecg-io-intellij

`nodecg-io-intellij` is an IntelliJ-Plugin that hosts an HTTP-Server to allow manipulation of the IDE via HTTP-Requests. This was developed for the [nodecg-io](https://github.com/codeoverflow-org/nodecg-io) project. You may take a look at it if you need examples on how to use this.

## How it works

When the plugin is installed it'll start an HTTP-Server on port `19524` when the IDE launches. If you're using multiple JetBrains IDEs simultaneously you should change this port for every IDE you have because every running instance of an IDE will start andown HTTP-Server.

To change the port you need to edit the `.vmoptions` file of your IDE. It's found in the installation directoy of your IDE by default. Add the following line:

```
-Dnodecg.io.port=12345
```

and replace `12345` with the port you want to use.


If your server crashes or whatever you can restart it via the menu entry `Tools/Restart nodecg-io Server`.

## How do I use this?

Check the implementation at [nodecg-io](https://github.com/noeppi-noeppi/nodecg-io/tree/intellij/nodecg-io-intellij).
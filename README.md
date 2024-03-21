<!--
 ___ _            _ _    _ _    __
/ __(_)_ __  _ __| (_)__(_) |_ /_/
\__ \ | '  \| '_ \ | / _| |  _/ -_)
|___/_|_|_|_| .__/_|_\__|_|\__\___|
            |_| 
-->
![](https://platform.simplicite.io/logos/standard/logo250.png)
* * *

`SimStore` module definition
============================

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=simplicite-modules-SimStore&metric=alert_status)](https://sonarcloud.io/dashboard?id=simplicite-modules-SimStore)

### Introduction

This is a **application store** management module.

### Import

To import this module:

- Create a module named `SimStore`
- Set the settings as:

```json
{
	"type": "git",
	"origin": {
		"uri": "https://github.com/simplicitesoftware/module-simstore.git"
	}
}
```

- Click on the _Import module_ button

### Configure

The URL(s) of the store file(s) is in the `STORE_SOURCE` system parameter.

### Quality

This module can be analysed by the **SonarQube** analysis tool
using this command:

```bash
mvn verify sonar:sonar
```

`StoStore` external object definition
-------------------------------------

Custom page for adding/removing modules from the stores



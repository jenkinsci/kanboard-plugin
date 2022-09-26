# Kanboard Jenkins plugin

A plugin that allows to create or update a Kanboard task as a post-build action, to trigger a build when a task is created or moved, and to fetch a task and its attachments as a build step.

This plugin requires that Kanboard be configured to use the "X-API-Auth" header for authentication as described in the documentation here: https://docs.kanboard.org/en/latest/api/authentication.html

Allows users to create or update a [Kanboard](https://kanboard.net/)
task as a post-build action, trigger a build when a task is created or
moved, and fetch a task and its attachments as a build
step.

[Kanboard](https://kanboard.net/) is an open source tool to manage
projects using a Lean
[Kanban](https://en.wikipedia.org/wiki/Kanban_(development)) approach.

**Requires Kanboard version \>= 1.0.36.**

### Change Log

##### Versions 1.5.12-1.5.13 (Sep 26, 2022

-   Fixes ClassCastException crashes with latest release of Kanboard.
-   Update project URL for documentation

##### Versions 1.5.9-1.5.11 (Sep 25, 2018)

-   Security fixes.

##### Version 1.5.8 (Mar 13, 2017)

-   The plugin now allows task tags management. You can add new tags (or
    remove existing ones) using a comma separated string of tags. Prefix
    a tag with "-" to remove it.

##### Version 1.5.7 (Dec 20, 2016)

-   A Kanboard task attachment maximum allowed size can be defined in
    the global configuration, attachments of a bigger size won't be sent
    to the Kanboard server.

##### Version 1.5.6 (Dec 18, 2016)

-   Renamed from kanboard-publisher-plugin to kanboard-plugin.

##### Version 1.5.4 (Dec 17, 2016)

-   Trigger now also fires on task move (column change), it was only
    available on task creation before.

##### Version 1.5.1 (Dec 16, 2016)

-   A new build step is now available that allows to fetch a Kanboard
    task and some of its attachments available through the
    KANBOARD\_TASKJSON and a few others environment variables.

##### Version 1.4 (Dec 16, 2016)

-   Can trigger a build when a Kanboard task is created, the
    corresponding task reference is exported to the KANBOARD\_TASKREF
    build environment variable.

##### Version 1.3.3 (Dec 15, 2016)

-   Allow to define task color via the KANBOARD\_TASKCOLOR environment
    variable.

##### Version 1.3.1 (Dec 14, 2016)

-   Internationalization and french translation added.

##### Version 1.2 (Dec 09, 2016)

-   Debug mode now globally configurable and disabled by default.

##### Version 1.1 (Dec 08, 2016)

-   Export Kanboard task URL as KANBOARD\_TASKURL environment variable.

##### Version 1.0 (Dec 06, 2016)

-   Initial version (requires Kanboard version \>= 1.0.31).

### Screenshots

![image](https://user-images.githubusercontent.com/385653/192309931-64e06c5c-2daf-450c-83b1-a6e235c4815a.png)

![image](https://user-images.githubusercontent.com/385653/192310009-f69ce4f0-d652-455e-abfa-c15221a52fcc.png)

![image](https://user-images.githubusercontent.com/385653/192310068-77571f7a-810e-4f2e-8a77-7c246508aa96.png)

![image](https://user-images.githubusercontent.com/385653/192310112-714200f2-3084-4254-a5ea-d34af81b53b6.png)



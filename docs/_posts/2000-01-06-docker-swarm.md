---
title: "Docker Swarm"
bg: edge_water
color: black
fa-icon: img/docker_swarm_icon.png
---

### Before starting

Setup a [docker swarm](https://docs.docker.com/get-started/part4/) with at least two nodes.

#### Constellations

* One manager and multiple workers. The hub will run on the manager and the 
created browser containers will run on the workers.
* A high available docker swarm with multiple managers. The manager that runs the hub will 
not run any created browser container.

_Info:_ Currently we do not support running created browser containers on the same node as
the hub. Running the browser containers and the hub on the same node leads to communication
problems, which we hope to fix soon.

#### Images

Pull images:
{% highlight shell %}
docker pull dosel/zalenium
docker pull elgalu/selenium
{% endhighlight %}


### Run Zalenium

Provide a docker-compose.yml file and deploy it:
{% highlight shell %}
docker stack deploy -c ./docker-compose.yml STACK
{% endhighlight %}

_Info:_ Use an appropriate name for `STACK`.

docker-compose.yml example:
{% highlight shell %}
version: "3.7"

services:
  zalenium:
    image: dosel/zalenium
    hostname: zalenium
    deploy:
      placement:
        constraints:
            - node.role == manager
    labels:
        - "de.zalando.gridRole=hub" # important for us to identify the node which runs zalenium hub
    ports:
        - "4444:4444"
        - "8000:8000" # port for remote debugging zalenium code
    networks:
        - zalenium # attachable overlay network to use
    volumes:
        - /var/run/docker.sock:/var/run/docker.sock
        - /tmp/videos:/home/seluser/videos
    environment:
        - PULL_SELENIUM_IMAGE=true
        - ZALENIUM_PROXY_CLEANUP_TIMEOUT=1800
    command: ["start", "--swarmOverlayNetwork", "STACK_zalenium", "--videoRecordingEnabled", "false"]

networks:
    zalenium:
        driver: overlay
        attachable: true

{% endhighlight %}

_Info:_ It is important to give the service that runs the zalenium hub the label
`"de.zalando.gridRole=hub"`. This helps to identify the node which runs the hub
and created browser containers will not be deployed on this node, which would cause
communication problems between the hub and the created browser containers.

Video recording and logs are currently not supported, but we hope to re-enable this
feature with docker swarm.

#### Network

The created overlay network must be passed as argument `--swarmOverlayNetwork` to zalenium,
which will actually switch Zalenium to Docker Swarm mode.

Make sure passing the network name with its stack name as prefix.

In our example we named our network "zalenium" and the stack was named "STACK" so the network
will have the name `"STACK_zalenium"`, which we passed to `"--swarmOverlayNetwork"`.

#### Options

If you want browser containers only deployed on workers set `SWARM_RUN_TESTS_ONLY_ON_WORKERS=1`
as environment variable.

### Technical Information

__Synchronized docker operations__

Docker operations run in synchronized blocks to prevent stale browser containers remain forever.

see also:
- [eclipse-ee4j/jersey#3772](https://github.com/eclipse-ee4j/jersey/issues/3772)
- [zalando/zalenium#808](https://github.com/zalando/zalenium/issues/808)

__One service per test session__

For each test session we deploy a new service that will create a browser container to run tests.

Working with one service and adapt the number of replicas does not work because we can't
control which browser containers will be removed when decreasing replicas. It can and
will happen that docker will remove a browser container with a running test to fulfill
number of replicas.


### Known Errors

Executed tests run into following forwarding errors:
- `was terminated due to FORWARDING_TO_NODE_FAILED`
- `cannot forward the request unexpected end of stream on Connection`
The docker swarm seems to be overloaded. Try to reduce `--maxDockerSeleniumContainers` to unload
your docker swarm system. A good value is the number of all cpu cores available in the docker swarm.





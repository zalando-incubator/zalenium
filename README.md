[![Codacy Badge](https://api.codacy.com/project/badge/Grade/c719a14f5537488b8fb95d70e27acd5f)](https://www.codacy.com/app/diemol_3/zalenium?utm_source=github.com&utm_medium=referral&utm_content=zalando/zalenium&utm_campaign=badger)
[![Build Status](https://travis-ci.org/zalando/zalenium.svg?branch=master)](https://travis-ci.org/zalando/zalenium)
[![Quality Gate](https://sonarqube.com/api/badges/gate?key=de.zalando.tip:zalenium)](https://sonarqube.com/dashboard/index/de.zalando.tip:zalenium)
[![codecov](https://codecov.io/gh/zalando/zalenium/branch/master/graph/badge.svg)](https://codecov.io/gh/zalando/zalenium)
[![](https://images.microbadger.com/badges/version/dosel/zalenium.svg)](https://microbadger.com/images/dosel/zalenium)
[![](https://images.microbadger.com/badges/version/dosel/zalenium:3.0.1c.svg)](https://microbadger.com/images/dosel/zalenium:3.0.1c)

# What is Zalenium?
A Selenium Grid extension to scale up and down your local grid dynamically with docker containers. It uses [docker-selenium](https://github.com/elgalu/docker-selenium) to run your tests in Firefox and Chrome locally, and when you need a different browser, your tests get redirected to [Sauce Labs](https://saucelabs.com/) and/or [BrowserStack](https://www.browserstack.com/) and/or [TestingBot](https://testingbot.com/).

### Why Zalenium?
We know how complicated is to have a stable grid to run UI tests with Selenium, and moreover how hard is to maintain it over time. It is also very difficult to have a local grid with enough capabilities to cover all browsers and platforms.

Therefore we are trying this approach where [docker-selenium](https://github.com/elgalu/docker-selenium) nodes are created, used and disposed on demand when possible. With this, you can run faster your UI tests with Firefox and Chrome since they are running on a local grid, on a node created from scratch and disposed after the test finishes.

And whenever you need a capability that cannot be fulfilled by [docker-selenium](https://github.com/elgalu/docker-selenium), then the test gets redirected to [Sauce Labs](https://saucelabs.com/) and/or [BrowserStack](https://www.browserstack.com/) and/or [TestingBot](https://testingbot.com/).

This creates Zalenium's main goal: to allow anyone to have a disposable and flexible Selenium Grid infrastructure.

The original idea comes from this [Sauce Labs post](https://saucelabs.com/blog/introducing-the-sauce-plugin-for-selenium-grid).

You can use the Zalenium already, but it is still under development and open for bug reporting, contributions and much more, see [contributing](CONTRIBUTING.md) for more details.

## Getting Started

#### Prerequisites
* Docker engine running, version >= 1.11.1 (probably works with earlier versions, not tested yet).
* Download the [docker-selenium](https://github.com/elgalu/docker-selenium) image. `docker pull elgalu/selenium`
* JDK8+
* *nix platform (tested only in OSX and Ubuntu, not tested on Windows yet).
* If you want to use the [Sauce Labs](https://saucelabs.com/) and/or the [BrowserStack](https://www.browserstack.com/) and/or the [TestingBot](https://testingbot.com/) feature, you need an account with them.

#### Setting it up
* Make sure your docker daemon is running
* `docker pull dosel/zalenium`

#### Running it
Zalenium uses docker to scale on-demand, therefore we need to give it the `docker.sock` full access, this is known as "Docker alongside docker".

NB. The container must be called `zalenium`. This is required because a docker network with this name will be created to allow all containers to locate each other without too much hassle.

* Basic usage, without any of the integrated cloud testing platforms enabled:

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      dosel/zalenium start 
  ```

* More usage examples can be seen [here](./docs/usage_examples.md)

* After the output, you should see the DockerSeleniumStarter node in the [grid](http://localhost:4444/grid/console)

* The startup can receive different parameters:
  * `--chromeContainers` -> Chrome nodes created on startup. Default is 1.
  * `--firefoxContainers` -> Firefox nodes created on startup. Default is 1.
  * `--maxDockerSeleniumContainers` -> Max number of docker-selenium containers running at the same time. Default is 10.
  * `--sauceLabsEnabled` -> Start Sauce Labs node or not. Defaults to 'false'.
  * `--browserStackEnabled` -> Start BrowserStack node or not. Defaults to 'false'.
  * `--testingbotEnabled` -> Start TestingBot node or not. Defaults to 'false'.
  * `--startTunnel` -> When using a cloud testing platform is enabled, starts the tunnel to allow local testing. Defaults to 'false'.
  * `--videoRecordingEnabled` -> Sets if video is recorded in every test. Defaults to 'true'.
  * `--screenWidth` -> Sets the screen width. Defaults to 1900.
  * `--screenHeight` -> Sets the screen height. Defaults to 1880.
  * `--timeZone` -> Sets the time zone in the containers. Defaults to "Europe/Berlin".

* Stop it: `docker stop zalenium`

#### Using it
* Just point your Selenium tests to [http://localhost:4444/wd/hub](http://localhost:4444/wd/hub) and that's it!
* You can use the [integration tests](./src/test/java/de/zalando/tip/zalenium/it/ParallelIT.java) we have to try Zalenium.
* Check the live preview of your running tests [http://localhost:4444/grid/admin/live](http://localhost:4444/grid/admin/live)
* To see the recorded videos, check the `/tmp/videos` folder (or the folder that you mapped when starting the container).
  * It is possible to customise the video name by adding a `name` capability in your test. The file name will look like `testName_browser_nodePort_timeStamp.mkv` 

### Docker version

#### Linux
For Linux systems you can simply share the docker binary via `-v $(which docker):/usr/bin/docker`

```sh
docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
  -v $(which docker):/usr/bin/docker \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /tmp/videos:/home/seluser/videos \
  dosel/zalenium start 
```

#### OSX
Zalenium for OSX is currently compatible with Docker `1.11` and `1.12` __default__. In Mac is recommended that you explicitly tell Zalenium which major version you are using via `-e DOCKER=1.11` due to API compatibility issues. In the future this will be automated on our side as it is with Linux (read above)

```sh
docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
  -e DOCKER=1.11 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /tmp/videos:/home/seluser/videos \
  dosel/zalenium start 
```

## Contributions
Any feedback or contributions are welcome! Please check our [guidelines](CONTRIBUTING.md), they just follow the general GitHub issue/PR flow.

#### TODOs
We would love some help with:
* Testing the tool in your day to day scenarios, to spot bugs or use cases we have not considered.
* Integrating it with CI tools.

#### Testing

If you want to verify your changes locally with the existing tests (please double check that the Docker daemon is running and that you can do `docker ps`):
* Only unit tests

    ```sh
        mvn test
    ```
* Unit and integration tests. You can specify the number of threads used to run the integration tests. If you omit the property, the default is one.

    ```sh
        mvn clean verify -Pintegration-test -DthreadCountProperty={numberOfThreads}
    ```


## How it works

![How it works](./images/how_it_works.gif)

Zalenium works conceptually in a simple way:

1. A Selenium Hub is started, listening to port 4444.
2. One custom node for [docker-selenium](https://github.com/elgalu/docker-selenium), and when enabled, one for [Sauce Labs](https://saucelabs.com/) and/or one for [BrowserStack](https://www.browserstack.com/) and/or one for [TestingBot](https://testingbot.com) are started and get registered to the grid.
3. When a test request arrives to the hub, the requested capabilities are verified against each one of the nodes.
4. If the request can be executed on [docker-selenium](https://github.com/elgalu/docker-selenium), a docker container is created on the run, and the test request is sent back to the hub while the new node registers.
5. After the hub acknowledges the new node, it processes the test request with it.
6. The test is executed, and then container is disposed.
7. If the test cannot be executed in [docker-selenium](https://github.com/elgalu/docker-selenium), it will processed by one of the enabled cloud testing platforms. It takes the HTTP request, adds the user and api key to it, and forwards it to the cloud platform.

Basically, the tool makes the grid expand or contract depending on the amount of requests received.

## Selenium 2 and Selenium 3 - About the project versioning
* To make life easy for people who want to use it, we are now using as a version number the Selenium version being supported.
* The major-minor version combined with the patch level will indicate the Selenium version being supported. E.g.
  * When a release is `2.53.1a`, it supports Selenium 2.53.1.
  * When a release is `3.0.0a`, it supports Selenium 3.0.0.
  * The badges above show the latest image versions for Selenium 2 and 3.
  
* The versioning will be similar to the one used in [docker-selenium](https://github.com/elgalu/docker-selenium)
* Until Selenium 3 gets more widely used than Selenium 2, `latest` will refer to the most recent docker image supporting Selenium 2. 

## Integrated Cloud Testing solutions
* Thanks to the open source accounts we got, we have integrated so far:

![BrowserStack](./images/browserstack_logo.png)    ![Sauce Labs](./images/saucelabs_logo.png)     ![TestingBot](./images/testingbot_logo.png)

If you want to integrate another cloud testing solution, we are happy to receive PRs or requests via issues, don't forget to check the [guidelines](CONTRIBUTING.md) for contributing.

License
===================

See [License](LICENSE.md)

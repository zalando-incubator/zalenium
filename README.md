[![Build Status](https://travis-ci.org/zalando-incubator/zalenium.svg?branch=master)](https://travis-ci.org/zalando-incubator/zalenium)
[![Quality Gate](https://sonarqube.com/api/badges/gate?key=de.zalando.tip:zalenium)](https://sonarqube.com/dashboard/index/de.zalando.tip:zalenium)
[![codecov](https://codecov.io/gh/zalando-incubator/zalenium/branch/master/graph/badge.svg)](https://codecov.io/gh/zalando-incubator/zalenium)

# What is Zalenium?
A Selenium Grid extension to scale up and down your local grid dynamically with docker containers. It uses [docker-selenium](https://github.com/elgalu/docker-selenium) to run your tests in Firefox and Chrome locally, and when you need a different browser, your tests get redirected to [Sauce Labs](https://saucelabs.com/).

### Why Zalenium?
We know how complicated is to have a stable grid to run UI tests with Selenium, and moreover how hard is to maintain it over time. It is also very difficult to have a local grid with enough capabilities to cover all browsers and platforms.

Therefore we are trying this approach where [docker-selenium](https://github.com/elgalu/docker-selenium) nodes are created, used and disposed on demand when possible. With this, you can run faster your UI tests with Firefox and Chrome since they are running on a local grid, on a node created from scratch and disposed after the test finishes.

And whenever you need a capability that cannot be fulfilled by [docker-selenium](https://github.com/elgalu/docker-selenium), then the test gets redirected to [Sauce Labs](https://saucelabs.com/).

This creates Zalenium's main goal: to allow anyone to have a disposable and flexible Selenium Grid infrastructure.

The original idea comes from this [Sauce Labs post](https://saucelabs.com/blog/introducing-the-sauce-plugin-for-selenium-grid).

You can use the Zalenium already, but it is still under development and open for bug reporting, contributions and much more, see [contributing](CONTRIBUTING.md) for more details.

## Getting Started

#### Prerequisites
* Docker engine running, version >= 1.12.1 (probably works with earlier versions, not tested yet).
* Download the [docker-selenium](https://github.com/elgalu/docker-selenium) image. `docker pull elgalu/selenium`
* JDK8+
* *nix platform (tested only in OSX and Ubuntu, not tested on Windows yet).
* If you want to use the [Sauce Labs](https://saucelabs.com/) feature, you need an account with them.

#### Setting it up
* Make sure your docker daemon is running
* `docker pull dosel/zalenium`
* If you want to use [Sauce Labs](https://saucelabs.com/), export your user and API key as environment variables
```sh
  export SAUCE_USERNAME=<your Sauce Labs username>
  export SAUCE_ACCESS_KEY=<your Sauce Labs access key>
```

#### Running it
Zalenium uses docker to scale on-demand, therefore we need to give it the `docker.sock` full access, this is known as "Docker alongside docker".
* Start it with Sauce Labs enabled:
  ```sh
    export SAUCE_USERNAME="<yourUser>"
    export SAUCE_ACCESS_KEY="<yourSecret>"
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -e SAUCE_USERNAME -e SAUCE_ACCESS_KEY \
      -v /tmp/videos:/home/seluser/videos \
      -v /var/run/docker.sock:/var/run/docker.sock \
      dosel/zalenium start
  ```

* Start it without Sauce Labs enabled:
  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      dosel/zalenium start --sauceLabsEnabled false
  ```

* After the output, you should see the DockerSeleniumStarter node and the SauceLabs node in the [grid](http://localhost:4444/grid/console)

* The startup can receive different parameters:
  * `--chromeContainers` -> Chrome nodes created on startup. Default is 1.
  * `--firefoxContainers` -> Firefox nodes created on startup. Default is 1.
  * `--maxDockerSeleniumContainers` -> Max number of docker-selenium containers running at the same time. Default is 10.
  * `--sauceLabsEnabled` -> Start Sauce Labs node or not. Defaults to 'true'.

* Stop it: `docker stop zalenium`

#### Using it
* Just point your Selenium tests to http://localhost:4444/wd/hub and that's it!
* You can use the [integration tests](./src/test/java/de/zalando/tip/zalenium/it/ParallelIT.java) we have to try Zalenium.
* To see the recorded videos, check the `videos` subfolder, located where the JARs are running.

## Contributions
Any feedback or contributions are welcome! Please check our [guidelines](CONTRIBUTING.md), they just follow the general GitHub issue/PR flow.

#### TODOs
We would love some help with:
* Testing the tool in your day to day scenarios, to spot bugs or use cases we have not considered.
* Adding more unit and integration tests.
* Adding more cloud testing platforms.
* Integrating it with CI tools.
* Upgrading it to Selenium 3 Beta.

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
2. One custom node for [docker-selenium](https://github.com/elgalu/docker-selenium) and one for [Sauce Labs](https://saucelabs.com/) are started and get registered to the grid.
3. When a test request arrives to the hub, the requested capabilities are verified against each one of the nodes.
4. If the request can be executed on [docker-selenium](https://github.com/elgalu/docker-selenium), a docker container is created on the run, and the test request is sent back to the hub while the new node registers.
5. After the hub acknowledges the new node, it processes the test request with it.
6. The test is executed, and then container is disposed.
7. If the test cannot be executed in [docker-selenium](https://github.com/elgalu/docker-selenium), it is processed by [Sauce Labs](https://saucelabs.com/). It takes the HTTP request, adds the Sauce Labs user and api key to it, and forwards it to the cloud platform.

Basically, the tool makes the grid expand or contract depending on the amount of requests received.

License
===================

Copyright 2016 Zalando SE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

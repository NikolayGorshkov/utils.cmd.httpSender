# utils.cmd.httpSender project

Small command-line tool for testing HTTP requests and responses. Features:

- starts from command line
- allows to paste response as text (e.g. from Chrome DevTools Network tab, together with headers)
- accepts request headers and body as single text block, no need to set headers and other staff with separate parameters
- prints raw response received, together with headers
- tries to format response
- works natively with HTTP/1 (sockets-based implementation), allowing to experiment with headers and body
- TLS sonnection ignores certificates verification
- Content-Length in request is automatically adjusted in case you have a request body
- written in Java :)
- compiles to single native executable (thanks to Quarkus and GraalVM)

Typical use case - make request in a browser (e.g. Google Chrome), copy raw headers and body to a text editor, edit, paste into the tool, press Alt+Enter to start request.

Supported flags:

- -tls - forse using TLS connection (if you want to check https://... links)

This project uses Quarkus, the Supersonic Subatomic Java Framework (https://quarkus.io/) :)

## Example

Let's request Google's first page with HTTPS and HTTP/1.1. Start the tool:

`utils.cmd.httpSender-0.1-runner -tls`

type:

```
GET / HTTP/1.1
Host: www.google.com
Connection: close
```

press Alt+Enter.

If you want to use POST with body, try:

```
POST / HTTP/1.1
Host: www.google.com
Connection: close
Content-Type: application/x-www-form-urlencoded
Content-Length: 5

q=zzz
```

and Alt+Enter, but Google don't support POST search queries, and will happily tell it to you.

If you copy headers from browser request, don't forget to replace value "Connection" header from
"keep-alive" to "close".

Another example for HTTP/1.1:

```
GET /?q=quarkus&format=xml HTTP/1.1
Host: api.duckduckgo.com
Connection: close
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9
Accept-Encoding: gzip, deflate
Accept-Language: en-US,en;q=0.9,ru;q=0.8
```

and for HTTP/2:

```
:authority: api.duckduckgo.com
:method: GET
:path: /?q=quarkus&format=xml
:scheme: https
accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9
accept-encoding: gzip, deflate
accept-language: en-US,en;q=0.9,ru;q=0.8
```

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
./mvnw quarkus:dev
```

To pass aorguments:
```
./mvnw quarkus:dev -Dquarkus.args='-tls'
```

To add arguments add, for example `-Dquarkus.args='--help'`

## Packaging and running the application

The application can be packaged using `./mvnw package`.
It produces the `utils.cmd.httpSender-0.1-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

The application is now runnable using `java -jar target/utils.cmd.httpSender-0.1-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: `./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/utils.cmd.httpSender-0.1-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image.

## Debugging the application in VSCode (with remote debug)

Add to .vscode/launch.json:

```
{
    "type": "java",
    "name": "Debug (Attach)",
    "request": "attach",
    "hostName": "localhost",
    "port": 5005
}
````

Run `mvn compile quarkus:dev -Ddebug`

Then connect to running Java process with the configuration above/

## Running native tests (doesn't work for this app right now)

```
./mvnw verify -Pnative
```
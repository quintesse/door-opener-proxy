# Door Opener Proxy

A JBang script for use with [NUKI](nukio.io) [Smart Locks](https://nuki.io/en/smart-lock/) and [Door Openers](https://nuki.io/en/opener/). It allows you to open doors from a public endpoint
without going through the NUKI Cloud API.

Why would you want that? Well the NUKI Cloud API works just fine
but it's protocol is somewhat complex, too complex at least for
my use-case: creating an NFC tag that will cause my mobile phone
to open my door.

For that I needed a simpler protocol but still have _some_ security.

To that end I've created two different scripts:

## app.java

A very simple one that has almost no security, except through obscurity: [app.java](./app.java).

The script keeps track of unauthorized requests and will block an IP for 5 minutes if 3 failed attempts have been made.

When run, it starts up an endpoint that listens for requests on http://localhost:8080/open and takes just two parameters: a `token` and a `door`:

- **token** - A security token. Defined at startup for each user.
- **door** - A door identifier. Possible values are also defined at startup.

## secure.java

A more secure version that uses typical industry safety measures: [secure.java](./secure.java).

The script keeps track of unauthorized requests and will block an IP or user for 5 minutes if 3 failed attempts have been made.

When run, it starts up an endpoint that also listens for requests on http://localhost:8080/open and takes the following parameters:

- **ts** - A timestamp of the moment the request is made.
- **cd** - A random code.
- **usr** - The user performing the request.
- **hash** - A SHA512 hash of `<ts>,<cd>,<password>`
- **door** - A door name. Possible values are also defined at startup.

## How to run

Both scripts need several environment variables to be set for them to work:

- **DOP_HOST** - The host name or IP of the NUKI Bridge.
- **DOP_TOKEN** - The NUKI Bridge API token to use (See **Bridge discovery & API activation** of [Nuki Bridge HTTP API](https://developer.nuki.io/page/nuki-bridge-http-api/4/)).
- **DOP_USERS** - A comma separated list of `<user>=<token_or_password>`.
- **DOP_DOORS** - A comma separated list of `<door_name>=<door_id>:<door_type>`. Where `<door_name>` is the name the user will use in the request's `door` parameter (see above), `<door_id>` is NUKI's id for a door lock or opener and `<door_type>` is `0` for a Smart Lock and `2` for an Opener.
- **DOP_RIGHTS** - A comma separated list of `<user>=<door_name>[:<door_name>]...` that defines which doors the user is allowed to operate.

After that the simplest way is to use [JBang](http://jbang.dev):

```
DOP_HOST=1.2.3.4 \
DOP_TOKEN=secret \
DOP_USERS=john=s3cr3t,guest=temp123 \
DOP_DOORS=front=12345:0,entrance=98765:2 \
DOP_RIGHTS=john=front:entrance,guest=entrance \
jbang secure.java
```

Or wrap that into a Docker container like this:

```
docker build -t my-door-opener-proxy .
docker run -ti --rm -p 8080:8080 \
-e DOP_HOST=1.2.3.4 \
-e DOP_TOKEN=secret \
-e DOP_USERS=john=s3cr3t,guest=temp123 \
-e DOP_DOORS=front=12345:0,entrance=98765:2 \
-e DOP_RIGHTS=john=front:entrance,guest=entrance \
my-door-opener-proxy
```

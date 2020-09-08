![Java CI](https://github.com/stargate/stargate/workflows/Java%20CI/badge.svg)

# Stargate

An open source API framework for data

Stargate was built with extensibility as a first class citizen;
customize it with new APIs, data types, and persistence engines to make your data work for you.

- For information about how to use Stargate, visit [stargate.io](https://stargate.io/)
- To ask questions, collaborate, and chat, visit our [Discord](https://discord.gg/Jv6jp8T)
- To learn how to participate in our community, visit our [community page](https://stargate.io/community)
- To set up and use a Stargate development environment, visit the [dev guide](DEVELOPERS_GUIDE.md)

## Contents
- [Introduction](#introduction)
- [Repositories](#repositories)
- [Issue Management](#issue-management)

## Introduction

Stargate is an open source API framework that acts as a data gateway proxy and lives in front of a database.
It enables customization of all aspects of data access and contains abstracted services for authentication, data APIs, request handling and routing, and persistence backends.
The current form implements an Apache Cassandra backend but there are no bounds to the databases or APIs that this framework can support.

Stargate is contains the following components:

- Coordinator: TODO
- API Services: TODO

    - cql: API implementation for the Cassandra Query Language
    - restapi: API implementation for exposing Cassandra data over REST

- Persistence Services: TODO

    - persistence-api: TODO
    - persistence-common: TODO

- Authentication Services: Responsible for authentication to Stargate

    - auth-api: REST service for generating auth token
    - auth-service-file: Service to store tokens in files
    - authentication: Interface for working with auth providers

- Filters: TODO

## Repositories

- stargate/stargate
- stargate/api-extensions
- stargate/persistence-extensions
- stargate/docs
- stargate/website

## Issue Management

TODO

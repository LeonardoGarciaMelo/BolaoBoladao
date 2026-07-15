# syntax=docker/dockerfile:1
FROM elixir:1.20.1-otp-28-slim AS base
RUN apt-get update && apt-get install -y --no-install-recommends build-essential git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN mix local.hex --force && mix local.rebar --force

FROM base AS test
ENV MIX_ENV=test
COPY mix.exs mix.lock .formatter.exs ./
RUN mix deps.get
COPY config config
COPY lib lib
COPY priv priv
COPY test test
CMD ["mix", "test"]

FROM base AS build
ENV MIX_ENV=prod
COPY mix.exs mix.lock ./
RUN mix deps.get --only prod
COPY config config
RUN mix deps.compile
COPY lib lib
COPY priv priv
RUN mix compile && mix release

FROM debian:trixie-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates libstdc++6 openssl libncurses6 curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --home-dir /app bolao
WORKDIR /app
COPY --from=build --chown=bolao:bolao /app/_build/prod/rel/boladao_pay ./
RUN chmod -R a+rX /app
USER bolao
EXPOSE 4000
CMD ["/bin/sh", "-lc", "bin/boladao_pay eval 'BoladaoPay.Release.migrate()' && bin/boladao_pay start"]

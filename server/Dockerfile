FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
COPY web ./web
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /agentchi-server .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /agentchi-server /agentchi-server
ENV CC_ADDR=:8080 CC_DATA=/data/state.json
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["/agentchi-server"]

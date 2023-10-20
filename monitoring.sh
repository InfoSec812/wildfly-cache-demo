#!/bin/bash

if ! podman volume exists prometheus-data
then
  podman volume create prometheus-data
fi

if ! podman volume exists grafana-data
then
  podman volume create grafana-data
fi

cat monitoring.yml | envsubst | tee last-pod-run.yml | podman play kube --replace -

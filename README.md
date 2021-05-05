# Tiny URL

A sample implementation of the Tiny URL service deployed on Kubernetes.

---
# Environment

- Kind: `kind v0.10.0 go1.15.7 darwin/amd64`
- Kubernetes:
```yaml
clientVersion:
  buildDate: "2021-04-08T21:16:14Z"
  compiler: gc
  gitCommit: cb303e613a121a29364f75cc67d3d580833a7479
  gitTreeState: clean
  gitVersion: v1.21.0
  goVersion: go1.16.3
  major: "1"
  minor: "21"
  platform: darwin/amd64
serverVersion:
  buildDate: "2021-01-21T01:11:42Z"
  compiler: gc
  gitCommit: faecb196815e248d3ecfb03c680a4507229c2a56
  gitTreeState: clean
  gitVersion: v1.20.2
  goVersion: go1.15.5
  major: "1"
  minor: "20"
  platform: linux/amd64
```

### Kubernetes Cluster

I used Kind to set up my Kubernetes cluster. Here's the configuration file, `kind-condig.yaml`, for my cluster.

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
    - |
      kind: InitConfiguration
      nodeRegistration:
        kubeletExtraArgs:
          node-labels: "ingress-ready=true"
  - role: worker
  - role: worker
```

```shell
kind create cluster --config k8s/kind-config.yaml
```

### Creating Development Certificates

Use the scripts from this [dev-certificates](https://github.com/BenMorel/dev-certificates) to generate root certificate and tinyurl.com certificate.

```shell
./create-ca.sh
./create-certificate.sh tinyurl.com
```

On macOS, import `ca.crt` into your keychain and update its Trust setting to "Always Trust".

### Updating `/etc/hosts` with domain names.

```text
127.0.0.1  prometheus-server.tinyurl.com, grafana.tinyurl.com
```

---

# Deploying Prometheus and Grafana

### Create a namespace named `monitoring` for monitoring applications.
```shell
kubectl apply -f k8s/monitoring/namespace.yaml
```

### Deploy prometheus into your cluster with in the `monitoring` namespace and mark it as `prometheus`.
```shell
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install prometheus prometheus-community/prometheus --namespace monitoring
```

### Check deployment status
```shell
kubectl get pods -n monitoring
```

```shell
NAME                                             READY   STATUS    RESTARTS   AGE
prometheus-alertmanager-ccf8f68cd-zmjkx          2/2     Running   0          3m56s
prometheus-kube-state-metrics-685b975bb7-gnclx   1/1     Running   0          3m56s
prometheus-node-exporter-dmzmb                   1/1     Running   0          3m56s
prometheus-node-exporter-jjwj5                   1/1     Running   0          3m57s
prometheus-pushgateway-74cb65b858-jl5dn          1/1     Running   0          3m56s
prometheus-server-d9fb67455-t6dsr                2/2     Running   0          3m56s
```

### Deploy grafana into your cluster with in the `monitoring` namespace and mark it as `grafana`.
```shell
kubectl apply -f k8s/monitoring/grafana/grafana.yaml
```

### Check deployment status
```shell
kubectl get pods -n monitoring
```

```shell
NAME                      READY   STATUS    RESTARTS   AGE
grafana-d5d85bcd6-hrpf4   1/1     Running   0          2m57s
```
---
### Deploying `nginx` as ingress controller

The first command install the ingress controller. The second waits until the ingress controller has been installed.

```shell
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=90s
```

Apply ingress rules -

```shell
kubectl apply -f k8s/monitoring/grafana/grafana.yaml
```

You should be able to access Prometheus and Grafana from your browser using the following URL's -

- Prometheus https://prometheus-server.tinyurl.com
- Grafana https://grafana.tinyurl.com

### Configuring Data Sources and Dashboards in Grafana

* Go to Settings > Data Sources <br>
  Add a new Data Source and select Prometheus from the list. Set URL to `prometheus-server.monitoring.svc.cluster.local`. Click `Save & Test` and ensure it works.

* Go to Grafana > Dashboards > Manage <br>
  Import [NGINX Ingress](https://grafana.com/grafana/dashboards/9614), [Kubernetes Cluster](https://grafana.com/grafana/dashboards/6417) <br> 
  Similarlym you can search for dashboards and import them.
---

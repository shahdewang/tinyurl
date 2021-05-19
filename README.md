# Tiny URL

A sample implementation of the Tiny URL service deployed on Kubernetes.

---

## Environment

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

---

## Deploying Prometheus and Grafana

### Create a namespace named `monitoring` for monitoring applications.
```shell
kubectl apply -f k8s/monitoring/namespace.yaml
```

### Deploy prometheus into your cluster within the `monitoring` namespace and name it as `prometheus`.
```shell
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
```

```shell
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

### Deploy grafana into your cluster within the `monitoring` namespace and name it as `grafana`.

```shell
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

```shell
helm install grafana grafana/grafana --namespace monitoring
```

Once Grafana is intalled, you can retrieve the admin password with the following command -

```shell
kubectl get secret --namespace monitoring grafana -o jsonpath="{.data.admin-password}" | base64 --decode ; echo
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

```shell
NAME                                             READY   STATUS    RESTARTS   AGE
grafana-65cc7f9746-9cjhq                         1/1     Running   0          2m18s
prometheus-alertmanager-ccf8f68cd-klz6l          2/2     Running   0          5m33s
prometheus-kube-state-metrics-685b975bb7-29hff   1/1     Running   0          5m33s
prometheus-node-exporter-5ws7k                   1/1     Running   0          5m33s
prometheus-node-exporter-p4jm8                   1/1     Running   0          5m33s
prometheus-pushgateway-74cb65b858-5qr6j          1/1     Running   0          5m33s
prometheus-server-d9fb67455-jhcsl                2/2     Running   0          5m33s
```

### Creating a TLS secret

Run the following command from the directory where the certificates were created.

```shell
kubectl create secret tls tinyurl-monitoring --key k8s/cert/tinyurl.com.key --cert k8s/cert/tinyurl.com.crt -n monitoring
```

Apply ingress rules -

```shell
kubectl apply -f k8s/monitoring/ingress-rules.yaml
```

### Updating `/etc/hosts` with domain names.

```text
127.0.0.1  prometheus-server.tinyurl.com, grafana.tinyurl.com
```

You should now be able to access Prometheus and Grafana from your browser using the following URL's -

- Prometheus https://prometheus-server.tinyurl.com
- Grafana https://grafana.tinyurl.com

### Configuring Data Sources and Dashboards in Grafana

* Go to Settings > Data Sources <br>
  Add a new Data Source and select Prometheus from the list. Set URL to `prometheus-server.monitoring.svc.cluster.local`. Click `Save & Test` and ensure it works.

* Go to Grafana > Dashboards > Manage <br>
  Import [Kubernetes Cluster](https://grafana.com/grafana/dashboards/6417) <br> 
  Similarly, you can import other dashboards.

---

## Deploying PostgreSQL

### Create a namespace named `monitoring` for monitoring applications.

```shell
kubectl apply -f k8s/postgres/namespace.yaml
```

### Deploy prometheus into your cluster with in the `monitoring` namespace and mark it as `prometheus`.
```shell
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

```shell
helm install postgresql bitnami/postgresql -n postgres -f k8s/postgres/values.yaml
```

Using port-forward connect to the database using local client.

```shell
kubectl port-forward --address localhost service/postgresql 5432:5432 -n postgres
```

---

## Deploying Zookeeper

```shell
kubectl apply -f k8s/zookeeper/zookeeper.yaml
```

Sanity test the ensenble. The command below executes the `zkCli.sh` script to write `world` to the path `/hello` on the `zk-0` Pod in the ensemble.

```shell
kubectl exec zk-0 zkCli.sh create /hello world
```

The output should look something like -

```shell
WATCHER::

WatchedEvent state:SyncConnected type:None path:null
Created /hello
```

To get the data from the `zk-1` Pod use the following command.

```shell
kubectl exec zk-1 zkCli.sh get /hello
```

The output should look something like -

```shell
WATCHER::

WatchedEvent state:SyncConnected type:None path:null
cZxid = 0x200000002
world
ctime = Tue May 18 20:58:42 UTC 2021
mZxid = 0x200000002
mtime = Tue May 18 20:58:42 UTC 2021
pZxid = 0x200000002
cversion = 0
dataVersion = 0
aclVersion = 0
ephemeralOwner = 0x0
dataLength = 5
numChildren = 0
```

Using port-forward connect to the ensemble using local client.

```shell
kubectl port-forward --address localhost service/zk-cs 2181:2181
```

Sanity testing ensemble using local client.

```shell
zkcli -s="127.0.0.1:2181" get /hello
```

---

## Deploying Tinyurl Service (WIP)

Create configmap with Postgresql and Zookeeper configuration that can be stored as literal.

```shell
kubectl create configmap service-config --from-literal=POSTGRES_SERVICE="postgresql.postgres.svc.cluster.local" --from-literal=POSTGRES_DB_USER=postgres --from-literal=ZK_SERVICE="zk-cs" 
```

Execute the following commands to create a secret to store Postgresql connection parameters.

```shell
POSTGRES_PASSWORD=$(kubectl get secret --namespace postgres postgresql -o jsonpath="{.data.postgresql-password}" | base64 --decode)
kubectl create secret generic db-security --from-literal=POSTGRES_DB_USER=postgres --from-literal=POSTGRES_DB_PASSWORD=${POSTGRES_PASSWORD}
```

Building an image of TinyURL service and uploading it to Kind cluster.

```shell
./gradlew dockerBuildImage
kind load docker-image bufferstack/tinyurl:latest
```

Deploy TinuURL service.

```shell
kubectl create secret tls tinyurl-service --key k8s/cert/tinyurl.com.key --cert k8s/cert/tinyurl.com.crt
helm install tinyurl tinyurl-chart
```

Update `/etc/hosts` with following entry.

```text
127.0.0.1  prometheus-server.tinyurl.com, grafana.tinyurl.com, service.tinyurl.com
```

The TinyURL Swagger page is accessible at [https://service.tinyurl.com/swagger-ui.html](https://service.tinyurl.com/swagger-ui.html).

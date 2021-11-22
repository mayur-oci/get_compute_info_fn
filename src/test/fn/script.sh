fn create context prometheus --provider oracle
fn use context prometheus

fn update context oracle.compartment-id ocid1.compartment.oc1..aaaaaaaapipdwb47b3nbfmcvp7l7dh6oi5vuu5cpkwtg75s6bra5mpezeuqq
fn update context api-url https://functions.us-phoenix-1.oraclecloud.com

fn update context registry phx.ocir.io/intrandallbarnes/promfnrepo
docker login -u 'intrandallbarnes/mayur.raleraskar@oracle.com' -p 'Xyz' phx.ocir.io

fn -v deploy --app get_compute_metadata --no-bump

#  docker buildx build --no-cache --progress=plain .
#  fn -v deploy --app get_compute_metadata --no-bump ; DEBUG=1 fn invoke get_compute_metadata get_compute_info_fn

#  curl -k -X GET  https://hjgxslvr7jm7va4qb6arvokdx4.apigateway.us-phoenix-1.oci.customer-oci.com/prom/fn


docker run --name prometheus\
    -p 9090:9090 \
    -v /etc/prometheus:/etc/prometheus \
    prom/prometheus


# fn -v deploy --app get_compute_metadata --no-bump ; DEBUG=1 fn invoke get_compute_metadata get_compute_info_fn


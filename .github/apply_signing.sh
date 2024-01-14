if [[ ! -z "$SIGNING_KEY" ]]; then
    echo $GOOGLE_SERVICES | base64 -d > app/google-services.json
    echo $SIGNING_KEY | base64 -d > key.jks
    echo "storeFile=key.jks
    storePassword=$KEY_STORE_PASSWORD
    keyAlias=$ALIAS
    keyPassword=$KEY_PASSWORD" >signing.properties
fi
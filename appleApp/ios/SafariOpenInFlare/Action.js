var OpenInFlareAction = function() {};

OpenInFlareAction.prototype = {
    run: function(arguments) {
        arguments.completionFunction({"url": document.URL});
    },

    finalize: function(arguments) {
        var flareURL = arguments["flareURL"];
        if (flareURL) {
            window.location.assign(flareURL);
        }
    }
};

var ExtensionPreprocessingJS = new OpenInFlareAction();

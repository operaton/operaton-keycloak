import "./keycloak.js";

// on page load forward user from login page to dashboard
if (window.location.hash === '#/login') {
    window.location.hash = "";
}

let operatonIdentityKeycloak = undefined;

const portalName = document.querySelector('base').attributes['app-root'].value;

async function fetchKeycloakOptions(optionsUrl) {
    const response = await fetch(optionsUrl);
    const options = await response.json();
    return options;
}

await fetchKeycloakOptions(portalName+"/app/keycloak/keycloak-options.json")
    .then(options => {
        if (options) {
            operatonIdentityKeycloak = new Keycloak(options);
            operatonIdentityKeycloak.onTokenExpired = () => operatonIdentityKeycloak.updateToken(options.minValidity);
            operatonIdentityKeycloak.onAuthRefreshError = () => operatonIdentityKeycloak.login(options)
                .catch(() => console.error('Login failed'));
            return operatonIdentityKeycloak.init({
                onLoad: 'login-required',
                checkLoginIframe: false,
                promiseType: 'native'
            });
        } else {
            return Promise.resolve;
        }
    }).then(() => {
            if (operatonIdentityKeycloak) {
                (function () {
                    const constantMock = window.fetch;
                    window.fetch = function () {
                        if (arguments[0].startsWith("/") || arguments[0].startsWith(window.location.origin)) {
                            var args = Object.assign({}, arguments[1]);
                            if (!args.headers) {
                                args.headers = new Headers();
                            }
                            if (args.headers instanceof Headers) {
                                args.headers.append('Authorization', 'Bearer ' + operatonIdentityKeycloak.token);
                            } else {
                                args.headers['Authorization'] = 'Bearer ' + operatonIdentityKeycloak.token;
                            }
                            return constantMock.apply(this, [arguments[0], args]);
                        } else {
                            return constantMock.apply(this, arguments);
                        }
                    }
                })();

                (function (open) {
                    XMLHttpRequest.prototype.open = function () {
                        open.apply(this, arguments);
                        if (arguments[1].startsWith("/") || arguments[1].startsWith(window.location.origin)) {
                            this.withCredentials = true;
                            this.setRequestHeader('Authorization', 'Bearer ' + operatonIdentityKeycloak.token);
                        }
                    };
                })(XMLHttpRequest.prototype.open);
            }
        }
    )


// observe document if logout link is rendered and override it for SSO logout
const observer = new MutationObserver(() => {
    const logoutListItem = document.querySelector("li.account li.logout");
    if (logoutListItem) {
        observer.disconnect();

        const oldLogoutLink = logoutListItem.getElementsByTagName("a")[0];
        // create a clone so no listeners are attached anymore
        const logoutLink = oldLogoutLink.cloneNode(true);
        logoutListItem.replaceChild(logoutLink, oldLogoutLink);

        logoutLink.href = '#'
        logoutLink.onclick = () => operatonIdentityKeycloak && operatonIdentityKeycloak.logout();
    }
});
observer.observe(document, { attributes: false, childList: true, characterData: false, subtree: true });


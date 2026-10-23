function replaceLogoutLink() {
  // find the logout button
  const logoutButton = document.querySelector("a.logout, .logout > a");
  // once the button is present replace it with new functionality
  if (logoutButton) {
    // create a clone so no listeners are attached anymore
    const newLogout = logoutButton.cloneNode(true);
    newLogout.setAttribute('href', 'logout'); // call server side logout handler
    logoutButton.replaceWith(newLogout);
    observer.disconnect();
  }
}

const observer = new MutationObserver(replaceLogoutLink);
observer.observe(document.body, {
  childList: true,
  subtree: true,
  attributes: false,
  characterData: false
});
replaceLogoutLink();

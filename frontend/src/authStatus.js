const authStatus = {
  isAuthenticated: false,
  authenticate(cb) {
    this.isAuthenticated = true;
    cb();
  },
  signout(cb) {
    this.isAuthenticated = false;
    cb();
  },
};
export default authStatus;

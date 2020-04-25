import store from '../store';

export default (to, from, next) => {
    const path = to.fullPath;
    const fromPath = from.fullPath;

    const user = store.state.auth.user;

    const go = p => next(path !== p ? p : undefined);

    const isAuthRoute = path === '/' || path === '/microsoft' || path === '/settings';
    const authAuthorized = (path === '/auth/discord' && fromPath === '/') ||
        (path === '/auth/microsoft' && (fromPath === '/microsoft' || fromPath === '/profile'));

    if (isAuthRoute || (path.startsWith('/auth/') && !authAuthorized) || (path === '/success' && fromPath !== '/settings')) {
        if (!user || !user.username) {
            return go('/');
        }

        if (!user.temp) {
            return go('/profile');
        }

        if (user.email) {
            return go('/settings');
        }

        if (user.username) {
            return go('/microsoft');
        }
    }

    if (path === '/profile' && (!user || user.temp)) {
        return go('/');
    }

    next();
};
// Dev-server proxy. Override the backend with API_TARGET when port 8080 is
// taken, e.g. `API_TARGET=http://localhost:8081 npm start`.
const target = process.env.API_TARGET ?? 'http://localhost:8080';

export default {
  '/api': { target, secure: false, changeOrigin: true },
  '/v3/api-docs': { target, secure: false },
};

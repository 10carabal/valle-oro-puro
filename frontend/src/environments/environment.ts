// Rutas RELATIVAS: en desarrollo el proxy de proxy.conf.json enruta /api/* al servicio
// correspondiente (auth :8081, catalog :8082, sales :8083). El navegador nunca habla
// directamente con los puertos del backend.
export const environment = {
  apiAuth: '/api/auth',
  apiCatalog: '/api/catalog',
  apiSales: '/api/sales',
};

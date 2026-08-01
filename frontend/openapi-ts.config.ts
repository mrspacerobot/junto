import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  client: '@hey-api/client-fetch',
  input: '../docs/openapi.json',
  output: 'src/api/generated',
});

import type { CapacitorConfig } from '@capacitor/cli'

const serverUrl = process.env.CAPACITOR_SERVER_URL
const isProductionBuild = process.env.NODE_ENV === 'production'
const isDevServerMode = !!serverUrl && !isProductionBuild
const allowDevCleartext = process.env.CAPACITOR_CLEAR_TEXT === 'true'

const config: CapacitorConfig = {
  appId: 'com.erp.mobile',
  appName: 'ERP Mobile',
  webDir: 'dist',
  appendUserAgent: 'ERP-Mobile-App',
  backgroundColor: '#ffffff',
  loggingBehavior: isProductionBuild ? 'none' : 'debug',
  plugins: {
    PushNotifications: {
      presentationOptions: ['badge', 'sound', 'banner', 'list']
    }
  },
  server: isDevServerMode
    ? {
        url: serverUrl,
        cleartext: allowDevCleartext
      }
    : undefined,
  android: {
    appendUserAgent: 'ERP-Mobile-App',
    backgroundColor: '#ffffff',
    allowMixedContent: false
  },
  ios: {
    appendUserAgent: 'ERP-Mobile-App',
    backgroundColor: '#ffffff',
    scrollEnabled: true
  }
}

export default config

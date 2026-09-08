'use strict'
const fs = require('fs')
const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const CompressionPlugin = require('compression-webpack-plugin')

const name = process.env.VUE_APP_TITLE || '企业管理系统' // 网页标题

const cliPortIndex = process.argv.indexOf('--port')
const cliPort = cliPortIndex >= 0 ? process.argv[cliPortIndex + 1] : undefined
const port = process.env.port || process.env.npm_config_port || cliPort || 1025 // 端口
const localGatewayUrl = process.env.LOCAL_TODO_GATEWAY_URL || 'http://localhost:8080'
const isDevelopment = process.env.NODE_ENV === 'development'
const enableDevSourceMap = process.env.VUE_APP_DEV_SOURCE_MAP === 'true'
const localHttpsKeyFile = process.env.LOCAL_MOBILE_HTTPS_KEY_FILE
const localHttpsCertFile = process.env.LOCAL_MOBILE_HTTPS_CERT_FILE
if (!!localHttpsKeyFile !== !!localHttpsCertFile) {
  throw new Error('LOCAL_MOBILE_HTTPS_KEY_FILE and LOCAL_MOBILE_HTTPS_CERT_FILE must be configured together')
}
const localHttps = localHttpsKeyFile && localHttpsCertFile
  ? {
      key: fs.readFileSync(localHttpsKeyFile),
      cert: fs.readFileSync(localHttpsCertFile)
    }
  : false
const compressionPlugins = isDevelopment ? [] : [
  // 参考文档可按需替换为团队内部地址
  new CompressionPlugin({
    cache: true,                                   // 复用压缩缓存，加快重复生产构建
    test: /\.(js|css|html|jpe?g|png|gif|svg)?$/i,  // 压缩文件格式
    threshold: 10240,                              // 仅压缩 10KB 以上资源，避免小文件无效开销
    filename: '[path][base].gz[query]',            // 压缩后的文件名
    algorithm: 'gzip',                             // 使用gzip压缩
    minRatio: 0.8,                                 // 压缩比例，小于 80% 的文件不会被压缩
    deleteOriginalAssets: false                    // 压缩后删除原文件
  })
]

// vue.config.js 配置说明
//官方vue.config.js 参考文档 https://cli.vuejs.org/zh/config/#css-loaderoptions
// 这里只列一部分，具体配置参考文档
module.exports = {
  // 部署生产环境和开发环境下的URL。
  // 默认情况下，Vue CLI 会假设你的应用是被部署在一个域名的根路径上
  // 例如 https://example.com/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://example.com/admin/，则设置 baseUrl 为 /admin/。
  publicPath: process.env.NODE_ENV === "production" ? "/" : "/",
  // 在npm run build 或 yarn build 时 ，生成文件的目录名称（要和baseUrl的生产环境路径一致）（默认dist）
  outputDir: 'dist',
  // 用于放置生成的静态资源 (js、css、img、fonts) 的；（项目打包之后，静态资源会放在这个文件夹下）
  assetsDir: 'static',
  // 如果你不需要生产环境的 source map，可以将其设置为 false 以加速生产环境构建。
  productionSourceMap: false,
  // Element UI 按需使用源码包，需要 Babel 处理其 JSX 渲染函数。
  transpileDependencies: ['quill', 'element-ui'],
  // webpack-dev-server 相关配置
  devServer: {
    host: '0.0.0.0',
    port: port,
    public: process.env.DEV_SERVER_PUBLIC || `localhost:${port}`,
    https: localHttps,
    open: true,
    historyApiFallback: {
      disableDotRule: true
    },
    proxy: {
      // detail: https://cli.vuejs.org/config/#devserver-proxy
      [process.env.VUE_APP_BASE_API]: {
        target: localGatewayUrl,
        changeOrigin: true,
        pathRewrite: {
          ['^' + process.env.VUE_APP_BASE_API]: ''
        }
      }
    },
    disableHostCheck: true
  },
  css: {
    loaderOptions: {
      sass: {
        sassOptions: { outputStyle: "expanded" }
      }
    }
  },
  configureWebpack: {
    name: name,
    devtool: isDevelopment
      ? (enableDevSourceMap ? 'eval-cheap-module-source-map' : false)
      : undefined,
    resolve: {
      alias: {
        '@': resolve('src')
      }
    },
    plugins: compressionPlugins,
    performance: {
      maxAssetSize: 800 * 1024,
      maxEntrypointSize: 1536 * 1024
    },
  },
  chainWebpack(config) {
    config.plugins.delete('preload') // TODO: need test
    config.plugins.delete('prefetch') // TODO: need test

    // set svg-sprite-loader
    config.module
      .rule('svg')
      .exclude.add(resolve('src/assets/icons'))
      .end()
    config.module
      .rule('icons')
      .test(/\.svg$/)
      .include.add(resolve('src/assets/icons'))
      .end()
      .use('svg-sprite-loader')
      .loader('svg-sprite-loader')
      .options({
        symbolId: 'icon-[name]'
      })
      .end()

    config.when(process.env.NODE_ENV !== 'development', config => {
          config
            .plugin('ScriptExtHtmlWebpackPlugin')
            .after('html')
            .use('script-ext-html-webpack-plugin', [{
            // `runtime` must same as runtimeChunk name. default is `runtime`
              inline: /runtime\..*\.js$/
            }])
            .end()

          config.optimization.splitChunks({
            chunks: 'all',
            cacheGroups: {
              libs: {
                name: 'chunk-libs',
                test: /[\\/]node_modules[\\/]/,
                priority: 10,
                chunks: 'initial' // only package third parties that are initially dependent
              },
              elementUI: {
                name: 'chunk-elementUI', // split elementUI into a single package
                test: /[\\/]node_modules[\\/]_?element-ui(.*)/, // in order to adapt to cnpm
                priority: 20, // the weight needs to be larger than libs and app or it will be packaged into libs or app
                chunks: 'initial' // advanced components keep their explicit async chunk boundaries
              },
              commons: {
                name: 'chunk-commons',
                test: resolve('src/components'), // can customize your rules
                minChunks: 3, //  minimum common number
                priority: 5,
                reuseExistingChunk: true
              }
            }
          })
          config.optimization.runtimeChunk('single')
    })
  }
}

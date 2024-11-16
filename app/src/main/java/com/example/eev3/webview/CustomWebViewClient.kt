package com.example.eev3.webview

import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.view.View

class CustomWebViewClient : WebViewClient() {
    
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // 开始加载时隐藏 WebView
        view?.visibility = View.INVISIBLE
        
        // 立即注入初始样式以防止闪烁
        view?.evaluateJavascript(
            """
            (function() {
                document.documentElement.style.visibility = 'hidden';
                var style = document.createElement('style');
                style.textContent = 'body { background: #1e1e1e !important; }';
                document.head.appendChild(style);
            })();
            """.trimIndent(),
            null
        )
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { webView ->
            // 注入样式和处理布局
            injectCustomStyle(webView)
            hideUnwantedElements(webView)
            
            // 延迟显示内容并尝试播放
            webView.postDelayed({
                webView.evaluateJavascript(
                    """
                    (function() {
                        document.documentElement.style.visibility = 'visible';
                        
                        // 创建一个音频上下文来解锁自动播放
                        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
                        if (audioContext.state === 'suspended') {
                            audioContext.resume();
                        }
                        
                        // 模拟用户交互
                        const event = new MouseEvent('mousedown', {
                            view: window,
                            bubbles: true,
                            cancelable: true
                        });
                        document.dispatchEvent(event);
                        
                        // 尝试播放的函数
                        function attemptPlay() {
                            try {
                                if (typeof jQuery !== 'undefined' && jQuery('#player').length > 0) {
                                    // 直接调用播放器API
                                    const jPlayer = jQuery('#player');
                                    if (!jPlayer.data('jPlayer')) {
                                        // 如果播放器未初始化，等待后重试
                                        setTimeout(attemptPlay, 100);
                                        return;
                                    }
                                    
                                    // 设置音频源并播放
                                    const mediaUrl = jPlayer.attr('data-url');
                                    if (mediaUrl) {
                                        jPlayer.jPlayer('setMedia', { mp3: mediaUrl });
                                        jPlayer.jPlayer('play');
                                    }
                                }
                            } catch (e) {
                                console.error('Play attempt failed:', e);
                                // 如果失败，稍后重试
                                setTimeout(attemptPlay, 500);
                            }
                        }
                        
                        // 开始尝试播放
                        attemptPlay();
                        
                        // 监听播放器就绪事件
                        jQuery('#player').on('jPlayer:ready', function() {
                            jQuery(this).jPlayer('play');
                        });
                    })();
                    """.trimIndent(),
                    null
                )
                webView.visibility = View.VISIBLE
            }, 1000)
        }
    }

    private fun injectCustomStyle(webView: WebView) {
        val css = """
            body {
                background: #1e1e1e !important;
                color: #ffffff !important;
                padding: 20px !important;
                margin: 0 !important;
                font-family: system-ui, -apple-system, sans-serif !important;
            }
            
            /* 移除旋转动画 */
            .rotate {
                animation: none !important;
                -webkit-animation: none !important;
            }
            
            /* 隐藏状态文字 */
            .jp-state-playing, .jp-state-paused {
                display: none !important;
            }
            
            /* 播放器容器样式 */
            #jp_container_1 {
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                max-width: 600px !important;
                margin: 0 auto !important;
                padding: 20px !important;
            }
            
            /* 隐藏原始布局 */
            .jp_right {
                display: none !important;
            }
            
            /* 封面图片容器样式 */
            .djpic {
                display: block !important;
                width: 200px !important;
                height: 200px !important;
                margin: 0 auto 20px !important;
                border-radius: 8px !important;
                overflow: hidden !important;
            }
            
            /* 封面图片样式 */
            #mcover {
                width: 100% !important;
                height: 100% !important;
                object-fit: cover !important;
                border-radius: 8px !important;
                display: block !important;
            }
            
            /* 标题样式 */
            .djname {
                width: 100% !important;
                text-align: center !important;
                margin: 20px 0 !important;
            }
            
            .djname h1 {
                font-size: 18px !important;
                margin: 0 !important;
                color: #ffffff !important;
            }
            
            /* 控制区域容器 */
            .player-controls {
                width: 100% !important;
                max-width: 400px !important;
                margin: 0 auto !important;
            }
            
            /* 进度条样式 */
            .jp-progress {
                background: rgba(255,255,255,0.1) !important;
                height: 6px !important;
                border-radius: 3px !important;
                margin: 15px 0 !important;
                cursor: pointer !important;
                width: 100% !important;
            }
            
            .jp-play-bar {
                background: #e60012 !important;
                height: 100% !important;
                border-radius: 3px !important;
            }
            
            /* 控制按钮容器样式 */
            .jp-controls {
                display: flex !important;
                justify-content: center !important;
                align-items: center !important;
                margin: 15px 0 !important;
            }
            
            /* 时间显示样式 */
            .jp-time-holder {
                text-align: center !important;
                color: rgba(255,255,255,0.8) !important;
                margin: 10px 0 !important;
            }
            
            /* 音量控制样式 */
            .jp-volume {
                display: flex !important;
                justify-content: center !important;
                align-items: center !important;
                margin: 10px 0 !important;
            }
            
            /* 歌词样式 */
            #play_geci {
                width: 100% !important;
                max-width: 400px !important;
                margin: 20px auto 0 !important;
                text-align: center !important;
                height: 200px !important;
                overflow-y: auto !important;
                color: rgba(255,255,255,0.8) !important;
            }

            /* 隐藏不需要的元素 */
            header, footer, .main > *:not(#player):not(#jp_container_1), 
            .play_list, .lksinger_list, .video_list, .mfooter, .fed-tabr-info {
                display: none !important;
            }
        """.trimIndent()

        webView.evaluateJavascript(
            """
            (function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = `$css`;
                document.head.appendChild(style);
            })();
            """.trimIndent(),
            null
        )
    }

    private fun hideUnwantedElements(webView: WebView) {
        val script = """
            (function() {
                // 先隐藏所有内容
                document.documentElement.style.visibility = 'hidden';
                
                // 保留原始播放器容器
                const player = document.getElementById('player');
                const container = document.getElementById('jp_container_1');
                
                if (!player || !container) return;
                
                // 创建新容器
                const wrapper = document.createElement('div');
                wrapper.style.maxWidth = '600px';
                wrapper.style.margin = '0 auto';
                wrapper.style.padding = '20px';
                
                // 重组播放器布局
                const controls = container.querySelector('.jp-controls');
                const progress = container.querySelector('.jp-progress');
                const timeHolder = container.querySelector('.jp-time-holder');
                const volume = container.querySelector('.jp-volume');
                const lyrics = container.querySelector('#play_geci');
                
                // 创建控制区域容器
                const controlsContainer = document.createElement('div');
                controlsContainer.className = 'player-controls';
                
                // 调整播放按钮容器的样式
                if (controls) {
                    // 创建一个新的容器来包裹播放按钮
                    const playButtonWrapper = document.createElement('div');
                    playButtonWrapper.style.cssText = `
                        display: flex;
                        justify-content: center;
                        width: 100%;
                        margin: 15px 0;
                    `;
                    
                    // 将播放按钮移动到新容器中
                    playButtonWrapper.appendChild(controls);
                    controlsContainer.appendChild(playButtonWrapper);
                }
                
                if (progress) controlsContainer.appendChild(progress);
                if (timeHolder) controlsContainer.appendChild(timeHolder);
                if (volume) controlsContainer.appendChild(volume);
                
                // 重新组织布局
                container.appendChild(controlsContainer);
                if (lyrics) container.appendChild(lyrics);
                
                // 移动播放器到新容器
                wrapper.appendChild(player);
                wrapper.appendChild(container);
                
                // 清空并重建页面
                document.body.innerHTML = '';
                document.body.appendChild(wrapper);
                
                // 移除所有其他脚本，但保留播放器必需的脚本
                const scripts = document.getElementsByTagName('script');
                const keepScripts = ['jplayer.min.js', 'jquery', 'common.js'];
                for (let i = scripts.length - 1; i >= 0; i--) {
                    const src = scripts[i].src;
                    if (!keepScripts.some(keep => src.includes(keep))) {
                        scripts[i].remove();
                    }
                }
                
                // 在页面重建完成后设置播放器
                function initPlayer() {
                    if (typeof jQuery !== 'undefined' && jQuery('#player').length > 0) {
                        try {
                            // 获取音频URL
                            var mediaUrl = jQuery('#player').attr('data-url');
                            if (mediaUrl) {
                                // 确保播放器已初始化
                                if (!jQuery('#player').data('jPlayer')) {
                                    jQuery('#player').jPlayer({
                                        ready: function () {
                                            const audioContext = new (window.AudioContext || window.webkitAudioContext)();
                                            if (audioContext.state === 'suspended') {
                                                audioContext.resume();
                                            }
                                            jQuery(this).jPlayer('setMedia', {
                                                mp3: mediaUrl
                                            }).jPlayer('play');
                                        },
                                        supplied: 'mp3',
                                        wmode: 'window',
                                        useStateClassSkin: true,
                                        autoBlur: false,
                                        smoothPlayBar: true,
                                        keyEnabled: true,
                                        remainingDuration: true,
                                        toggleDuration: true
                                    });
                                } else {
                                    jQuery('#player').jPlayer('play');
                                }
                            }
                        } catch (e) {
                            console.error('Player initialization failed:', e);
                            setTimeout(initPlayer, 500);
                        }
                    } else {
                        setTimeout(initPlayer, 500);
                    }
                }
                
                // 开始初始化播放器
                initPlayer();
                
                // 移除状态文字
                const stateTexts = document.querySelectorAll('.jp-state-playing, .jp-state-paused');
                stateTexts.forEach(text => text.remove());
                
                // 移除封面图片的旋转类
                const coverImage = document.querySelector('#mcover');
                if (coverImage) {
                    coverImage.classList.remove('rotate');
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }
}


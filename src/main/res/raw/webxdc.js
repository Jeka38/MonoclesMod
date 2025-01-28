// Based on GPLv3 code from deltachat-android
// https://github.com/deltachat/deltachat-android/blob/master/res/raw/webxdc.js

window.webxdc = (() => {
	let setUpdateListenerPromise = null
	var update_listener = () => {};
	var last_serial = 0;
	var realtime_listener = (data) => {};

	window.__webxdcUpdate = () => {
		var updates = JSON.parse(InternalJSApi.getStatusUpdates(last_serial));
		updates.forEach((update) => {
				update_listener(update);
				last_serial = update.serial;
		});
		if (setUpdateListenerPromise) {
			setUpdateListenerPromise();
			setUpdateListenerPromise = null;
		}
	};

	window.__webxdcRealtimeData = (data) => {
		realtime_listener(Uint8Array.from(atob(data), c => c.charCodeAt(0)));
	};

	return {
		selfAddr: InternalJSApi.selfAddr(),

		selfName: InternalJSApi.selfName(),

		setUpdateListener: (cb, serial) => {
				last_serial = typeof serial === "undefined" ? 0 : parseInt(serial);
				update_listener = cb;
				var promise = new Promise((res, _rej) => {
					setUpdateListenerPromise = res;
				});
				window.__webxdcUpdate();
				return promise;
		},

		sendUpdate: (payload, descr) => {
			InternalJSApi.sendStatusUpdate(JSON.stringify(payload), descr);
		},

		importFiles: (filters) => {
			var element = document.createElement("input");
			element.type = "file";
			element.accept = [
					...(filters.extensions || []),
					...(filters.mimeTypes || []),
			].join(",");
			element.multiple = filters.multiple || false;
			const promise = new Promise((resolve, _reject) => {
					element.onchange = (_ev) => {
							const files = Array.from(element.files || []);
							document.body.removeChild(element);
							resolve(files);
					};
			});
			element.style.display = "none";
			document.body.appendChild(element);
			element.click();
			return promise;
		},

		sendToChat: async (message) => {
			const data = {};
			if (!message.file && !message.text) {
				return Promise.reject("sendToChat() error: file or text missing");
			}
			const blobToBase64 = (file) => {
				const dataStart = ";base64,";
				return new Promise((resolve, reject) => {
					const reader = new FileReader();
					reader.readAsDataURL(file);
					reader.onload = () => {
						let data = reader.result;
						resolve(data.slice(data.indexOf(dataStart) + dataStart.length));
					};
					reader.onerror = () => reject(reader.error);
				});
			};
			if (message.text) {
				data.text = message.text;
			}

			if (message.file) {
				let base64content;
				if (!message.file.name) {
					return Promise.reject("sendToChat() error: file name missing");
				}
				if (
					Object.keys(message.file).filter((key) =>
						["blob", "base64", "plainText"].includes(key)
					).length > 1
				) {
					return Promise.reject("sendToChat() error: only one of blob, base64 or plainText allowed");
				}

				if (message.file.blob instanceof Blob) {
					base64content = await blobToBase64(message.file.blob);
				} else if (typeof message.file.base64 === "string") {
					base64content = message.file.base64;
				} else if (typeof message.file.plainText === "string") {
					base64content = await blobToBase64(
						new Blob([message.file.plainText])
					);
				} else {
					return Promise.reject("sendToChat() error: none of blob, base64 or plainText set correctly");
				}
				data.base64 = base64content;
				data.name = message.file.name;
			}

			const errorMsg = InternalJSApi.sendToChat(JSON.stringify(data));
			if (errorMsg) {
				return Promise.reject(errorMsg);
			}
		},

		joinRealtimeChannel: () => {
			return {
				leave: () => {},
				send: (data) => {
					if (!(data instanceof Uint8Array)) {
						throw new Error('realtime listener data must be a Uint8Array')
					}
					InternalJSApi.sendRealtime(data);
				},
				setListener: (listener) => {
					realtime_listener = listener;
				}
			};
		},

	};
})();

var Store = (function() {
	var data;
	function fire(src) {
		data = prepareData(src);
		render(0);
	}
	
	function render(tab_index) {
		var r = $(Mustache.render($('#tmplStore').html(), data));
		// tab initialization
		r.find('.tabitem').hide();
		r.find('#tab'+tab_index).show();
		r.find('.nav li[data-tab="'+tab_index+'"]').addClass('active');
		r.find('.nav li').click(tab);
		
		r.find('.install-module').click(callInstall);
		r.find('.goto-module').click(function() {
			$ui.openForm(null, "Module", $(this).data('module-id'));
		});
		r.find('.goto-delete').click(function() {
			$ui.displayModuleDelete(null, $(this).data('module-id'));
		});
		$('#store').html(r);
	}

	function tab() {
		$('#store .nav li').removeClass('active');
		$(this).addClass('active');
		
		$('#store .tabitem').hide();
		$('#tab'+$(this).data('tab')).show();
	}
	
	function prepareData(src) {
		$.extend(src, JSON.parse($ui.getAjax().T('STO_FRONT_TEXTS')));
		return src;
	}
	
	function callInstall() {
		var module = $(this).data('module');
		var storeIdx = $(this).data('store-idx');
		var url = data.install_url+"?install="+module+"&storeidx="+storeIdx;
		$ui.view.showLoading();

		// 5.4 asynchronous install with tracking
		if ($ui.view.form.tracker) {
			// 1) only create one empty module on server side
			$.ajax({
				url: url + "&track=true",
				type: 'GET',
				success: function(updated_data) {
					data = prepareData(updated_data);
					render(storeIdx);
					install(module);
				},
				error: function(request,error) {
					alert("Request: "+JSON.stringify(request));
				}
			});

		}
		else { // legacy synchronous
			$.ajax({
				url: url,
				type: 'GET',
				success: function(updated_data) {
					data = prepareData(updated_data);
					render(storeIdx);
					$ui.view.hideLoading();
					optionalClearCache();
				},
				error: function(request,error) {
					alert("Request: "+JSON.stringify(request));
				}
			});
		}
	}
	
	function install(module) {
		// 2) launch ModuleImport on client to track the progression
		var m = $ui.getApp().getBusinessObject("Module","store_ajax_Module");
		m.search({ mdl_name: module }).then(l => {
			m.item = l[0];
			m.action("ModuleImport"); // async
			$ui.view.hideLoading();
			var dlg = $ui.view.form.tracker({ name:"tkstore" }, {
				title: "Import " + module,
				progress: fn => {
					m.action("ModuleImport", { track:true }).then(fn);
				},
				done: () => {
					$ui.view.tools.dialogClose(dlg);
					optionalClearCache();
				},
				bar: $('<div/>') // empty action bar 
			});
		});
	}

	function optionalClearCache() {
		$ui.view.tools.dialog({
			title: $ui.getAjax().T("INFO"),
			content: data.tsl_installed_module,
			buttons:[{
				name: "SYS_CLEAR_CACHE",
				style: "primary",
				callback: function() { $ui.clearCache("dc"); }
			}, {
				name: "CLOSE",
				style: "default"
			}]
		});
	}
	
	return { fire: fire };
})();
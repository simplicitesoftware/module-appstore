var Store = (function() {
	var data;
	function fire(src){
		data = prepareData(src);
		render(0);
	}
	
	function render(tab_index){
		var r = $(Mustache.render($('#tmplStore').html(), data));
		// tab initialization
		r.find('.tabitem').hide();
		r.find('#tab'+tab_index).show();
		r.find('.nav li[data-tab="'+tab_index+'"]').addClass('active');
		r.find('.nav li').click(tab);
		
		r.find('.install-module').click(callInstall);
		r.find('.goto-module').click(function(){
			$ui.clickReference(null, "Module", $(this).data('module-id'));
		});
		r.find('.goto-delete').click(function(){
			$ui.loadURL(null, data.delete_url+$(this).data('module-id'));
		});
		$('#store').html(r);
	}

	function tab(){
		$('#store .nav li').removeClass('active');
		$(this).addClass('active');
		
		$('#store .tabitem').hide();
		$('#tab'+$(this).data('tab')).show();
	}
	
	function prepareData(src){
		src.stores.map(function(store){
			store.apps.map(function(app){
				app.module_settings = JSON.stringify(app.module_settings, null, 4);
				return app;
			});
			return store;
		});
		$.extend(src, JSON.parse($ui.getAjax().T('STO_FRONT_TEXTS')));
		return src;
	}
	
	function callInstall(){
		$ui.view.showLoading();
		var module = $(this).data('module');
		var storeIdx = $(this).data('store-idx');
		$.ajax({
	        'url' : data.install_url+"?install="+module+"&storeidx="+storeIdx,
	        'type' : 'GET',
	        'success' : function(updated_data) {
	            data = prepareData(updated_data);
	            render(storeIdx);
	            $ui.view.hideLoading();
	            optionalClearCache();
	        },
	        'error' : function(request,error)
	        {
	            alert("Request: "+JSON.stringify(request));
	        }
	    });
	}
	
	function optionalClearCache(){
		$ui.view.tools.dialog({
			title: $ui.getAjax().T("INFO"),
			content: data.tsl_installed_module,
			buttons:[{
				name: "SYS_CLEAR_CACHE",
				style: "primary",
				callback: function() { $ui.clearCache("dc"); }
			},{
				name: "CLOSE",
				style: "default"
			}]
		});
	}
	
    return { fire: fire };
})();